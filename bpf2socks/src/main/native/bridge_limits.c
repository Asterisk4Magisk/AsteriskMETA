// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "bridge_internal.h"

#include <errno.h>
#include <limits.h>
#include <sys/resource.h>

#define BPF2SOCKS_TCP_FDS_PER_SESSION 6ULL
#define BPF2SOCKS_UDP_FDS_PER_SESSION 2ULL
#define BPF2SOCKS_UDP_FDS_PER_BINDING 1ULL

uint32_t bpf2socks_worker_quota(uint32_t total, uint32_t worker_id, uint32_t worker_count) {
    if (worker_count == 0U || worker_id >= worker_count) return 0U;
    return total / worker_count + (worker_id < total % worker_count ? 1U : 0U);
}

static uint64_t session_fd_demand(
    uint32_t max_tcp_sessions,
    uint32_t max_udp_sessions,
    uint32_t max_udp_bindings) {
    uint64_t total = (uint64_t)max_tcp_sessions * BPF2SOCKS_TCP_FDS_PER_SESSION;
    total += (uint64_t)max_udp_sessions * BPF2SOCKS_UDP_FDS_PER_SESSION;
    total += (uint64_t)max_udp_bindings * BPF2SOCKS_UDP_FDS_PER_BINDING;
    return total;
}

int bpf2socks_fit_session_capacity(
    uint64_t nofile_limit,
    uint64_t reserve_fds,
    struct bpf2socks_session_capacity *capacity) {
    if (capacity == NULL || capacity->worker_count == 0U || capacity->max_tcp_sessions == 0U) {
        errno = EINVAL;
        return -1;
    }
    if (capacity->max_udp_sessions == 0U) {
        errno = EINVAL;
        return -1;
    }
    if (capacity->max_udp_bindings < capacity->max_udp_sessions) {
        capacity->max_udp_bindings = capacity->max_udp_sessions;
    }
    if (nofile_limit == UINT64_MAX) return 0;
    if (nofile_limit == 0U || nofile_limit <= reserve_fds) {
        errno = EMFILE;
        return -1;
    }

    uint64_t available_fds = nofile_limit - reserve_fds;
    uint64_t requested_fds = session_fd_demand(
        capacity->max_tcp_sessions,
        capacity->max_udp_sessions,
        capacity->max_udp_bindings);
    if (requested_fds <= available_fds) return 0;

    uint32_t tcp_sessions = (uint32_t)((uint64_t)capacity->max_tcp_sessions * available_fds / requested_fds);
    uint32_t udp_sessions =
        (uint32_t)((uint64_t)capacity->max_udp_sessions * available_fds / requested_fds);
    uint32_t udp_bindings =
        (uint32_t)((uint64_t)capacity->max_udp_bindings * available_fds / requested_fds);
    if (tcp_sessions < capacity->worker_count ||
        udp_sessions < capacity->worker_count ||
        udp_bindings < udp_sessions) {
        errno = EMFILE;
        return -1;
    }

    uint64_t used_fds = session_fd_demand(tcp_sessions, udp_sessions, udp_bindings);
    if (used_fds < available_fds) {
        uint64_t grow = available_fds - used_fds;
        uint64_t binding_room = (uint64_t)capacity->max_udp_bindings - udp_bindings;
        if (grow > binding_room) grow = binding_room;
        udp_bindings += (uint32_t)grow;
    }
    capacity->max_tcp_sessions = tcp_sessions;
    capacity->max_udp_sessions = udp_sessions;
    capacity->max_udp_bindings = udp_bindings;
    return 0;
}

bool bpf2socks_tcp_connection_timed_out(
    uint64_t now_ms,
    uint64_t created_at_ms,
    uint64_t last_activity_ms,
    bool relay_established,
    uint32_t connect_timeout_ms,
    uint32_t idle_timeout_ms) {
    if (now_ms == 0U) return false;
    if (!relay_established) {
        if (created_at_ms == 0U) return false;
        return connect_timeout_ms != 0U && now_ms >= created_at_ms &&
            now_ms - created_at_ms >= (uint64_t)connect_timeout_ms;
    }
    if (last_activity_ms == 0U) return false;
    return idle_timeout_ms != 0U && now_ms >= last_activity_ms &&
        now_ms - last_activity_ms >= (uint64_t)idle_timeout_ms;
}

int bpf2socks_nofile_soft_limit(uint64_t *out_limit) {
    if (out_limit == NULL) {
        errno = EINVAL;
        return -1;
    }
    struct rlimit limit;
    if (getrlimit(RLIMIT_NOFILE, &limit) != 0) return -1;
    *out_limit = limit.rlim_cur == RLIM_INFINITY ? UINT64_MAX : (uint64_t)limit.rlim_cur;
    return 0;
}

void bpf2socks_pending_budget_init(struct bpf2socks_udp_pending_budget *budget, size_t cap_bytes) {
    if (budget == NULL) return;
    budget->cap_bytes = cap_bytes;
    atomic_init(&budget->used_bytes, 0U);
    atomic_init(&budget->peak_bytes, 0U);
}

static void update_pending_budget_peak(struct bpf2socks_udp_pending_budget *budget, size_t used_bytes) {
    size_t peak = atomic_load_explicit(&budget->peak_bytes, memory_order_relaxed);
    while (peak < used_bytes &&
        !atomic_compare_exchange_weak_explicit(
            &budget->peak_bytes,
            &peak,
            used_bytes,
            memory_order_relaxed,
            memory_order_relaxed)) {
    }
}

int bpf2socks_pending_budget_reserve(struct bpf2socks_udp_pending_budget *budget, size_t bytes) {
    if (budget == NULL) {
        errno = EINVAL;
        return -1;
    }
    size_t used = atomic_load_explicit(&budget->used_bytes, memory_order_relaxed);
    for (;;) {
        if (bytes > budget->cap_bytes || used > budget->cap_bytes - bytes) {
            errno = ENOBUFS;
            return -1;
        }
        size_t next = used + bytes;
        if (atomic_compare_exchange_weak_explicit(
                &budget->used_bytes,
                &used,
                next,
                memory_order_acq_rel,
                memory_order_relaxed)) {
            update_pending_budget_peak(budget, next);
            return 0;
        }
    }
}

int bpf2socks_pending_budget_release(struct bpf2socks_udp_pending_budget *budget, size_t bytes) {
    if (budget == NULL) {
        errno = EINVAL;
        return -1;
    }
    size_t used = atomic_load_explicit(&budget->used_bytes, memory_order_relaxed);
    for (;;) {
        if (bytes > used) {
            errno = ERANGE;
            return -1;
        }
        if (atomic_compare_exchange_weak_explicit(
                &budget->used_bytes,
                &used,
                used - bytes,
                memory_order_acq_rel,
                memory_order_relaxed)) {
            return 0;
        }
    }
}

size_t bpf2socks_pending_budget_used(const struct bpf2socks_udp_pending_budget *budget) {
    return budget == NULL ? 0U : atomic_load_explicit(&budget->used_bytes, memory_order_relaxed);
}

size_t bpf2socks_pending_budget_peak(const struct bpf2socks_udp_pending_budget *budget) {
    return budget == NULL ? 0U : atomic_load_explicit(&budget->peak_bytes, memory_order_relaxed);
}

int bpf2socks_raise_nofile_limit(uint32_t requested_limit) {
    struct rlimit limit;
    if (getrlimit(RLIMIT_NOFILE, &limit) != 0) return -1;
    rlim_t target = (rlim_t)requested_limit;
    if (target > limit.rlim_max) target = limit.rlim_max;
    if (limit.rlim_cur >= target) return 0;
    limit.rlim_cur = target;
    return setrlimit(RLIMIT_NOFILE, &limit);
}
