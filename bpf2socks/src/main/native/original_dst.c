// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "bpf2socks.h"

#include <errno.h>
#include <netinet/in.h>
#include <stdbool.h>
#include <string.h>
#include <sys/socket.h>

#ifndef IP_ORIGDSTADDR
#define IP_ORIGDSTADDR 20
#endif

#ifndef IPV6_ORIGDSTADDR
#define IPV6_ORIGDSTADDR 74
#endif

static bool ipv6_is_token_address(
    const uint8_t addr[16],
    const struct bpf2socks_runtime_config *config) {
    if (config == NULL || config->token_ipv6_prefix_bits == 0U) return false;
    return bpf2socks_ipv6_matches_token_prefix(
        addr,
        config->token_ipv6_prefix,
        config->token_ipv6_prefix_bits);
}

static int sockaddr_in_to_original(
    const struct sockaddr_in *addr,
    const struct bpf2socks_runtime_config *config,
    uint8_t protocol,
    struct bpf2socks_original_dst *original) {
    if (addr == NULL || original == NULL || addr->sin_family != AF_INET) {
        errno = EINVAL;
        return -1;
    }
    if (config != NULL && bpf2socks_ipv4_matches_token_prefix(
            addr->sin_addr.s_addr,
            config->token_ipv4_prefix,
            config->token_ipv4_prefix_bits)) {
        errno = EADDRNOTAVAIL;
        return -1;
    }

    memset(original, 0, sizeof(*original));
    original->family = AF_INET;
    original->protocol = protocol;
    original->port = ntohs(addr->sin_port);
    memcpy(original->addr, &addr->sin_addr, 4U);
    return 0;
}

static int sockaddr_in6_to_original(
    const struct sockaddr_in6 *addr,
    const struct bpf2socks_runtime_config *config,
    uint8_t protocol,
    struct bpf2socks_original_dst *original) {
    if (addr == NULL || original == NULL || addr->sin6_family != AF_INET6) {
        errno = EINVAL;
        return -1;
    }
    const uint8_t *addr_bytes = addr->sin6_addr.s6_addr;
    if (IN6_IS_ADDR_UNSPECIFIED(&addr->sin6_addr) ||
        IN6_IS_ADDR_LOOPBACK(&addr->sin6_addr) ||
        ipv6_is_token_address(addr_bytes, config)) {
        errno = EADDRNOTAVAIL;
        return -1;
    }

    memset(original, 0, sizeof(*original));
    original->family = AF_INET6;
    original->protocol = protocol;
    original->port = ntohs(addr->sin6_port);
    memcpy(original->addr, addr_bytes, 16U);
    return 0;
}

int bpf2socks_original_from_sockaddr_storage(
    const struct sockaddr_storage *addr,
    const struct bpf2socks_runtime_config *config,
    uint8_t protocol,
    struct bpf2socks_original_dst *original) {
    if (addr == NULL || original == NULL) {
        errno = EINVAL;
        return -1;
    }
    if (addr->ss_family == AF_INET) {
        return sockaddr_in_to_original((const struct sockaddr_in *)addr, config, protocol, original);
    }
    if (addr->ss_family == AF_INET6) {
        return sockaddr_in6_to_original((const struct sockaddr_in6 *)addr, config, protocol, original);
    }
    errno = EAFNOSUPPORT;
    return -1;
}

int bpf2socks_original_from_cmsg(
    const struct cmsghdr *cmsg,
    const struct bpf2socks_runtime_config *config,
    uint8_t protocol,
    struct bpf2socks_original_dst *original) {
    if (cmsg == NULL || original == NULL) {
        errno = EINVAL;
        return -1;
    }
    if (cmsg->cmsg_level == IPPROTO_IP && cmsg->cmsg_type == IP_ORIGDSTADDR) {
        if (cmsg->cmsg_len < CMSG_LEN(sizeof(struct sockaddr_in))) {
            errno = EINVAL;
            return -1;
        }
        return sockaddr_in_to_original(
            (const struct sockaddr_in *)CMSG_DATA(cmsg),
            config,
            protocol,
            original);
    }
    if (cmsg->cmsg_level == IPPROTO_IPV6 && cmsg->cmsg_type == IPV6_ORIGDSTADDR) {
        if (cmsg->cmsg_len < CMSG_LEN(sizeof(struct sockaddr_in6))) {
            errno = EINVAL;
            return -1;
        }
        return sockaddr_in6_to_original((const struct sockaddr_in6 *)CMSG_DATA(cmsg), config, protocol, original);
    }
    errno = ENOENT;
    return -1;
}
