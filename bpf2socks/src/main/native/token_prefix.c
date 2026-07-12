// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "bpf2socks.h"

#include <arpa/inet.h>
#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

uint32_t bpf2socks_ipv4_token_host_mask(uint32_t prefix_bits) {
    if (prefix_bits > 32U) return 0U;
    if (prefix_bits == 0U) return UINT32_MAX;
    if (prefix_bits == 32U) return 0U;
    return UINT32_MAX >> prefix_bits;
}

uint32_t bpf2socks_ipv4_token_prefix_imm(const uint8_t prefix[4], uint32_t prefix_bits) {
    if (prefix == NULL || prefix_bits > 32U) return 0U;
    uint32_t addr_net = 0U;
    memcpy(&addr_net, prefix, sizeof(addr_net));
    return ntohl(addr_net) & ~bpf2socks_ipv4_token_host_mask(prefix_bits);
}

bool bpf2socks_ipv4_matches_token_prefix(
    uint32_t addr_net,
    const uint8_t prefix[4],
    uint32_t prefix_bits) {
    if (prefix == NULL || prefix_bits > 32U) return false;
    uint32_t prefix_net = 0U;
    memcpy(&prefix_net, prefix, sizeof(prefix_net));
    uint32_t mask = ~bpf2socks_ipv4_token_host_mask(prefix_bits);
    return (ntohl(addr_net) & mask) == (ntohl(prefix_net) & mask);
}

int bpf2socks_parse_token_ipv4_prefix(
    const char *text,
    uint8_t out[4],
    uint32_t *prefix_bits) {
    if (text == NULL || out == NULL || prefix_bits == NULL) {
        errno = EINVAL;
        return -1;
    }
    const char *slash = strchr(text, '/');
    if (slash == NULL || strcmp(slash, "/8") != 0) {
        errno = EINVAL;
        return -1;
    }
    size_t address_length = (size_t)(slash - text);
    if (address_length == 0U || address_length >= INET_ADDRSTRLEN) {
        errno = EINVAL;
        return -1;
    }
    char address[INET_ADDRSTRLEN];
    memcpy(address, text, address_length);
    address[address_length] = '\0';
    uint8_t parsed[4];
    if (inet_pton(AF_INET, address, parsed) != 1 ||
        memcmp(parsed, (const uint8_t[]){127U, 0U, 0U, 0U}, sizeof(parsed)) != 0) {
        errno = EINVAL;
        return -1;
    }
    memcpy(out, parsed, sizeof(parsed));
    *prefix_bits = BPF2SOCKS_TOKEN_IPV4_PREFIX_BITS;
    return 0;
}

int bpf2socks_parse_token_ipv6_prefix(
    const char *text,
    uint8_t out[16],
    uint32_t *prefix_bits) {
    if (text == NULL || out == NULL || prefix_bits == NULL) {
        errno = EINVAL;
        return -1;
    }
    char buffer[INET6_ADDRSTRLEN + 5U];
    int written = snprintf(buffer, sizeof(buffer), "%s", text);
    if (written < 0 || (size_t)written >= sizeof(buffer)) {
        errno = EINVAL;
        return -1;
    }
    char *slash = strchr(buffer, '/');
    if (slash == NULL) {
        errno = EINVAL;
        return -1;
    }
    *slash++ = '\0';
    char *end = NULL;
    errno = 0;
    unsigned long parsed_bits = strtoul(slash, &end, 10);
    if (errno != 0 || end == slash || *end != '\0' ||
        parsed_bits != BPF2SOCKS_TOKEN_IPV6_PREFIX_BITS ||
        inet_pton(AF_INET6, buffer, out) != 1) {
        errno = EINVAL;
        return -1;
    }
    memset(out + 8U, 0, 8U);
    *prefix_bits = (uint32_t)parsed_bits;
    return 0;
}

bool bpf2socks_ipv6_matches_token_prefix(
    const uint8_t addr[16],
    const uint8_t prefix[16],
    uint32_t prefix_bits) {
    if (addr == NULL || prefix == NULL || prefix_bits > 128U) return false;
    if (prefix_bits == 0U) return true;
    uint32_t full_bytes = prefix_bits / 8U;
    uint32_t remaining_bits = prefix_bits % 8U;
    if (full_bytes > 0U && memcmp(addr, prefix, full_bytes) != 0) return false;
    if (remaining_bits == 0U) return true;
    uint8_t mask = (uint8_t)(0xffU << (8U - remaining_bits));
    return (addr[full_bytes] & mask) == (prefix[full_bytes] & mask);
}

uint32_t bpf2socks_ipv6_token_word(const uint8_t prefix[16], size_t offset) {
    if (prefix == NULL || offset > 12U) return 0U;
    uint32_t value = 0U;
    memcpy(&value, prefix + offset, sizeof(value));
    return value;
}
