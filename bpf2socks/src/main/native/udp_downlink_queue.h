// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#ifndef ASTERISKMETA_BPF2SOCKS_UDP_DOWNLINK_QUEUE_H
#define ASTERISKMETA_BPF2SOCKS_UDP_DOWNLINK_QUEUE_H

#include <stdbool.h>
#include <stddef.h>

enum bpf2socks_udp_downlink_send_result {
    BPF2SOCKS_UDP_DOWNLINK_SEND_COMPLETE,
    BPF2SOCKS_UDP_DOWNLINK_SEND_RETRY,
    BPF2SOCKS_UDP_DOWNLINK_SEND_FATAL,
};

struct bpf2socks_udp_downlink_queue_node {
    struct bpf2socks_udp_downlink_queue_node *next;
};

struct bpf2socks_udp_downlink_queue {
    struct bpf2socks_udp_downlink_queue_node *head;
    struct bpf2socks_udp_downlink_queue_node *tail;
    size_t count;
};

struct bpf2socks_udp_downlink_waiter_tracker {
    size_t count;
};

enum bpf2socks_udp_downlink_send_result bpf2socks_udp_downlink_classify_send_result(
    int sent,
    unsigned int requested,
    int send_errno);
void bpf2socks_udp_downlink_queue_init(struct bpf2socks_udp_downlink_queue *queue);
void bpf2socks_udp_downlink_queue_push(
    struct bpf2socks_udp_downlink_queue *queue,
    struct bpf2socks_udp_downlink_queue_node *node);
struct bpf2socks_udp_downlink_queue_node *bpf2socks_udp_downlink_queue_pop(
    struct bpf2socks_udp_downlink_queue *queue);
void bpf2socks_udp_downlink_waiter_tracker_init(
    struct bpf2socks_udp_downlink_waiter_tracker *tracker);
bool bpf2socks_udp_downlink_waiter_tracker_mark_waiting(
    struct bpf2socks_udp_downlink_waiter_tracker *tracker,
    bool *waiting);
bool bpf2socks_udp_downlink_waiter_tracker_mark_ready(
    struct bpf2socks_udp_downlink_waiter_tracker *tracker,
    bool *waiting);
bool bpf2socks_udp_downlink_waiter_tracker_has_waiters(
    const struct bpf2socks_udp_downlink_waiter_tracker *tracker);

#endif
