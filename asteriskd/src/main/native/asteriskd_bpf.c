// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "asteriskd.h"

#include <arpa/inet.h>
#include <errno.h>
#include <linux/bpf.h>
#include <linux/unistd.h>
#include <stdint.h>
#include <string.h>
#include <sys/syscall.h>
#include <unistd.h>

#ifndef __NR_bpf
#if defined(__aarch64__)
#define __NR_bpf 280
#elif defined(__arm__)
#define __NR_bpf 386
#elif defined(__i386__)
#define __NR_bpf 357
#elif defined(__x86_64__)
#define __NR_bpf 321
#endif
#endif

struct asteriskd_lpm4_key {
    uint32_t prefixlen;
    uint32_t addr;
};

struct asteriskd_lpm6_key {
    uint32_t prefixlen;
    uint8_t addr[16];
};

static long bpf_sys(enum bpf_cmd command, union bpf_attr *attributes, unsigned int size) {
    return syscall(__NR_bpf, command, attributes, size);
}

static int open_pinned_map(const char *path) {
    union bpf_attr attributes;
    memset(&attributes, 0, sizeof(attributes));
    attributes.pathname = (uint64_t)(uintptr_t)path;
    return (int)bpf_sys(BPF_OBJ_GET, &attributes, sizeof(attributes));
}

static int update_map(int map_fd, const void *key) {
    uint8_t value = 1U;
    union bpf_attr attributes;
    memset(&attributes, 0, sizeof(attributes));
    attributes.map_fd = (uint32_t)map_fd;
    attributes.key = (uint64_t)(uintptr_t)key;
    attributes.value = (uint64_t)(uintptr_t)&value;
    attributes.flags = BPF_ANY;
    return (int)bpf_sys(BPF_MAP_UPDATE_ELEM, &attributes, sizeof(attributes));
}

static int get_next_key(int map_fd, const void *key, void *next_key) {
    union bpf_attr attributes;
    memset(&attributes, 0, sizeof(attributes));
    attributes.map_fd = (uint32_t)map_fd;
    attributes.key = (uint64_t)(uintptr_t)key;
    attributes.next_key = (uint64_t)(uintptr_t)next_key;
    return (int)bpf_sys(BPF_MAP_GET_NEXT_KEY, &attributes, sizeof(attributes));
}

static int delete_map(int map_fd, const void *key) {
    union bpf_attr attributes;
    memset(&attributes, 0, sizeof(attributes));
    attributes.map_fd = (uint32_t)map_fd;
    attributes.key = (uint64_t)(uintptr_t)key;
    return (int)bpf_sys(BPF_MAP_DELETE_ELEM, &attributes, sizeof(attributes));
}

static bool wanted_v4(const struct asteriskd_address_set *addresses, const struct asteriskd_lpm4_key *key) {
    if (key->prefixlen != 32U) return false;
    for (size_t index = 0U; index < addresses->count; ++index) {
        struct in_addr address;
        if (inet_pton(AF_INET, addresses->values[index], &address) == 1 && address.s_addr == key->addr) return true;
    }
    return false;
}

static bool wanted_v6(const struct asteriskd_address_set *addresses, const struct asteriskd_lpm6_key *key) {
    if (key->prefixlen != 128U) return false;
    for (size_t index = 0U; index < addresses->count; ++index) {
        struct in6_addr address;
        if (inet_pton(AF_INET6, addresses->values[index], &address) == 1 &&
            memcmp(address.s6_addr, key->addr, sizeof(key->addr)) == 0) {
            return true;
        }
    }
    return false;
}

int asteriskd_replace_lpm4_map(const char *pin_path, const struct asteriskd_address_set *addresses) {
    if (pin_path == NULL || pin_path[0] != '/' || addresses == NULL || addresses->family != AF_INET) {
        errno = EINVAL;
        return -1;
    }
    int map_fd = open_pinned_map(pin_path);
    if (map_fd < 0) return -1;
    int result = 0;
    for (size_t index = 0U; index < addresses->count; ++index) {
        struct asteriskd_lpm4_key key = {.prefixlen = 32U, .addr = 0U};
        if (inet_pton(AF_INET, addresses->values[index], &key.addr) != 1 || update_map(map_fd, &key) != 0) {
            result = -1;
            goto done;
        }
    }
    struct asteriskd_lpm4_key existing[ASTERISKD_MAX_ADDRESSES];
    size_t existing_count = 0U;
    struct asteriskd_lpm4_key current;
    struct asteriskd_lpm4_key next;
    bool have_current = false;
    while (get_next_key(map_fd, have_current ? &current : NULL, &next) == 0) {
        if (existing_count >= ASTERISKD_MAX_ADDRESSES) {
            errno = ENOSPC;
            result = -1;
            goto done;
        }
        existing[existing_count++] = next;
        current = next;
        have_current = true;
    }
    if (errno != ENOENT) {
        result = -1;
        goto done;
    }
    for (size_t index = 0U; index < existing_count; ++index) {
        if (!wanted_v4(addresses, &existing[index]) && delete_map(map_fd, &existing[index]) != 0) {
            result = -1;
            goto done;
        }
    }
done:
    (void)close(map_fd);
    return result;
}

int asteriskd_replace_lpm6_map(const char *pin_path, const struct asteriskd_address_set *addresses) {
    if (pin_path == NULL || pin_path[0] != '/' || addresses == NULL || addresses->family != AF_INET6) {
        errno = EINVAL;
        return -1;
    }
    int map_fd = open_pinned_map(pin_path);
    if (map_fd < 0) return -1;
    int result = 0;
    for (size_t index = 0U; index < addresses->count; ++index) {
        struct asteriskd_lpm6_key key = {.prefixlen = 128U, .addr = {0U}};
        if (inet_pton(AF_INET6, addresses->values[index], key.addr) != 1 || update_map(map_fd, &key) != 0) {
            result = -1;
            goto done;
        }
    }
    struct asteriskd_lpm6_key existing[ASTERISKD_MAX_ADDRESSES];
    size_t existing_count = 0U;
    struct asteriskd_lpm6_key current;
    struct asteriskd_lpm6_key next;
    bool have_current = false;
    while (get_next_key(map_fd, have_current ? &current : NULL, &next) == 0) {
        if (existing_count >= ASTERISKD_MAX_ADDRESSES) {
            errno = ENOSPC;
            result = -1;
            goto done;
        }
        existing[existing_count++] = next;
        current = next;
        have_current = true;
    }
    if (errno != ENOENT) {
        result = -1;
        goto done;
    }
    for (size_t index = 0U; index < existing_count; ++index) {
        if (!wanted_v6(addresses, &existing[index]) && delete_map(map_fd, &existing[index]) != 0) {
            result = -1;
            goto done;
        }
    }
done:
    (void)close(map_fd);
    return result;
}




