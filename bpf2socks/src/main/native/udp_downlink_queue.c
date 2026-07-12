// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "udp_downlink_queue.h"

#include <errno.h>

enum bpf2socks_udp_downlink_send_result bpf2socks_udp_downlink_classify_send_result(
    int sent,
    unsigned int requested,
    int send_errno) {
    if (requested == 0U) return sent == 0
        ? BPF2SOCKS_UDP_DOWNLINK_SEND_COMPLETE
        : BPF2SOCKS_UDP_DOWNLINK_SEND_FATAL;
    if (sent == (int)requested) return BPF2SOCKS_UDP_DOWNLINK_SEND_COMPLETE;
    if (sent >= 0 && (unsigned int)sent < requested) return BPF2SOCKS_UDP_DOWNLINK_SEND_RETRY;
    if (sent < 0 &&
        (send_errno == EAGAIN || send_errno == EWOULDBLOCK || send_errno == ENOBUFS || send_errno == EINTR)) {
        return BPF2SOCKS_UDP_DOWNLINK_SEND_RETRY;
    }
    return BPF2SOCKS_UDP_DOWNLINK_SEND_FATAL;
}

void bpf2socks_udp_downlink_queue_init(struct bpf2socks_udp_downlink_queue *queue) {
    if (queue == NULL) return;
    queue->head = NULL;
    queue->tail = NULL;
    queue->count = 0U;
}

void bpf2socks_udp_downlink_queue_push(
    struct bpf2socks_udp_downlink_queue *queue,
    struct bpf2socks_udp_downlink_queue_node *node) {
    if (queue == NULL || node == NULL) return;
    node->next = NULL;
    if (queue->tail != NULL) {
        queue->tail->next = node;
    } else {
        queue->head = node;
    }
    queue->tail = node;
    ++queue->count;
}

struct bpf2socks_udp_downlink_queue_node *bpf2socks_udp_downlink_queue_pop(
    struct bpf2socks_udp_downlink_queue *queue) {
    if (queue == NULL || queue->head == NULL) return NULL;
    struct bpf2socks_udp_downlink_queue_node *node = queue->head;
    queue->head = node->next;
    if (queue->head == NULL) queue->tail = NULL;
    node->next = NULL;
    --queue->count;
    return node;
}

void bpf2socks_udp_downlink_waiter_tracker_init(
    struct bpf2socks_udp_downlink_waiter_tracker *tracker) {
    if (tracker == NULL) return;
    tracker->count = 0U;
}

bool bpf2socks_udp_downlink_waiter_tracker_mark_waiting(
    struct bpf2socks_udp_downlink_waiter_tracker *tracker,
    bool *waiting) {
    if (tracker == NULL || waiting == NULL || *waiting) return false;
    *waiting = true;
    ++tracker->count;
    return true;
}

bool bpf2socks_udp_downlink_waiter_tracker_mark_ready(
    struct bpf2socks_udp_downlink_waiter_tracker *tracker,
    bool *waiting) {
    if (tracker == NULL || waiting == NULL || !*waiting) return false;
    *waiting = false;
    if (tracker->count > 0U) --tracker->count;
    return true;
}

bool bpf2socks_udp_downlink_waiter_tracker_has_waiters(
    const struct bpf2socks_udp_downlink_waiter_tracker *tracker) {
    return tracker != NULL && tracker->count > 0U;
}
