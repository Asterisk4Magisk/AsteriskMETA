// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "connected_udp_redirect.h"

#include <sys/socket.h>

bool bpf2socks_connected_udp_rewrites_peer_during_connect(int family) {
    return family == AF_INET || family == AF_INET6;
}
