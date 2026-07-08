// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "bridge_internal.h"

#include <stdint.h>

uint32_t bpf2socks_hash_bytes(const void *data, size_t len) {
    const uint8_t *ptr = data;
    uint32_t hash = 2166136261U;
    for (size_t i = 0; i < len; ++i) {
        hash ^= ptr[i];
        hash *= 16777619U;
    }
    return hash;
}

uint32_t bpf2socks_session_hash_bucket(uint32_t hash) {
    return hash % BPF2SOCKS_SESSION_HASH_BUCKETS;
}

static int above_pressure_watermark(uint64_t count, uint64_t cap) {
    return cap > 0U && count * 100U >= cap * 15U;
}

uint32_t bpf2socks_udp_effective_idle_timeout(
    uint32_t configured_timeout,
    uint32_t default_timeout,
    uint64_t session_count,
    uint64_t session_cap,
    uint64_t binding_count,
    uint64_t binding_cap,
    int pending) {
    uint32_t timeout = configured_timeout != 0U ? configured_timeout : default_timeout;
    if (timeout == 0U) timeout = 120U;
    if (pending) return timeout < 5U ? timeout : 5U;
    if (above_pressure_watermark(session_count, session_cap) ||
        above_pressure_watermark(binding_count, binding_cap)) {
        uint32_t pressured = timeout / 4U;
        if (pressured == 0U) pressured = 1U;
        return pressured < 10U ? pressured : 10U;
    }
    return timeout;
}

void bpf2socks_session_lru_detach(struct bpf2socks_udp_client_session *session) {
    if (session == NULL) return;
    if (session->lru_prev != NULL) session->lru_prev->lru_next = session->lru_next;
    if (session->lru_next != NULL) session->lru_next->lru_prev = session->lru_prev;
    session->lru_prev = NULL;
    session->lru_next = NULL;
}
