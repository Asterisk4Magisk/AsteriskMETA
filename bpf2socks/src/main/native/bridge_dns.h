// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#ifndef ASTERISK_BPF2SOCKS_BRIDGE_DNS_H
#define ASTERISK_BPF2SOCKS_BRIDGE_DNS_H

#include "bpf2socks.h"

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <sys/socket.h>

struct bpf2socks_dns_packet_info {
    uint16_t id;
    uint32_t question_fingerprint;
    bool has_question;
};

struct bpf2socks_dns_transaction {
    bool used;
    uint32_t channel_index;
    uint16_t rewritten_id;
    uint16_t original_id;
    uint32_t question_fingerprint;
    uint64_t sent_at_ms;
    uint64_t response_generation;
    struct sockaddr_storage client_addr;
    socklen_t client_addr_len;
    struct bpf2socks_sockaddr original_dst;
    int token_family;
    uint8_t token_addr[16];
    bool connected_udp_token;
    bool original_from_socket;
    int reply_fd;
    bool reply_raw;
    size_t lru_prev;
    size_t lru_next;
};

struct bpf2socks_dns_table {
    struct bpf2socks_dns_transaction *transactions;
    int32_t *lookup;
    uint16_t *next_ids;
    uint64_t *response_generations;
    size_t capacity;
    size_t count;
    uint32_t channel_count;
    size_t lru_head;
    size_t lru_tail;
};

int bpf2socks_dns_parse_query(
    const uint8_t *packet,
    size_t packet_len,
    struct bpf2socks_dns_packet_info *out);
int bpf2socks_dns_parse_response(
    const uint8_t *packet,
    size_t packet_len,
    struct bpf2socks_dns_packet_info *out);
void bpf2socks_dns_set_id(uint8_t *packet, size_t packet_len, uint16_t id);

int bpf2socks_dns_table_init(
    struct bpf2socks_dns_table *table,
    uint32_t channel_count,
    size_t capacity);
void bpf2socks_dns_table_free(struct bpf2socks_dns_table *table);
struct bpf2socks_dns_transaction *bpf2socks_dns_table_alloc(
    struct bpf2socks_dns_table *table,
    uint32_t preferred_channel,
    uint16_t original_id,
    uint32_t question_fingerprint,
    uint64_t now_ms);
struct bpf2socks_dns_transaction *bpf2socks_dns_table_find(
    struct bpf2socks_dns_table *table,
    uint32_t channel_index,
    uint16_t rewritten_id);
void bpf2socks_dns_table_note_response(
    struct bpf2socks_dns_table *table,
    uint32_t channel_index);
void bpf2socks_dns_table_release(
    struct bpf2socks_dns_table *table,
    struct bpf2socks_dns_transaction *tx);
size_t bpf2socks_dns_table_expire(
    struct bpf2socks_dns_table *table,
    uint64_t now_ms,
    uint64_t timeout_ms,
    size_t max_expire,
    bool *stale_channels,
    size_t stale_channel_count);
size_t bpf2socks_dns_table_count(const struct bpf2socks_dns_table *table);

#endif
