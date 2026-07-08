// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "bridge_dns.h"

#include <errno.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#define DNS_HEADER_SIZE 12U
#define DNS_QR_RESPONSE 0x80U
#define DNS_NAME_POINTER 0xc0U
#define DNS_NAME_POINTER_MASK 0x3fffU
#define DNS_MAX_POINTER_HOPS 16U
#define DNS_FREE_INDEX ((size_t)-1)
#define DNS_LRU_NONE ((size_t)-1)

static uint16_t read_be16(const uint8_t *packet, size_t offset) {
    return ((uint16_t)packet[offset] << 8) | packet[offset + 1U];
}

static uint32_t fnv1a_update(uint32_t hash, uint8_t value) {
    hash ^= value;
    hash *= 16777619U;
    return hash;
}

static uint8_t ascii_lower(uint8_t value) {
    return value >= 'A' && value <= 'Z' ? (uint8_t)(value + ('a' - 'A')) : value;
}

static int fingerprint_name(
    const uint8_t *packet,
    size_t packet_len,
    size_t offset,
    size_t *next_offset,
    uint32_t *hash) {
    size_t cursor = offset;
    size_t end_offset = 0U;
    bool jumped = false;
    uint32_t hops = 0U;

    while (cursor < packet_len) {
        uint8_t len = packet[cursor];
        if ((len & DNS_NAME_POINTER) == DNS_NAME_POINTER) {
            if (cursor + 1U >= packet_len) return -1;
            uint16_t pointer = read_be16(packet, cursor) & DNS_NAME_POINTER_MASK;
            if ((size_t)pointer >= packet_len || ++hops > DNS_MAX_POINTER_HOPS) return -1;
            if (!jumped) {
                end_offset = cursor + 2U;
                jumped = true;
            }
            cursor = pointer;
            continue;
        }
        if ((len & DNS_NAME_POINTER) != 0U) return -1;
        ++cursor;
        if (len == 0U) {
            if (next_offset != NULL) *next_offset = jumped ? end_offset : cursor;
            *hash = fnv1a_update(*hash, 0U);
            return 0;
        }
        if (len > 63U || cursor + len > packet_len) return -1;
        *hash = fnv1a_update(*hash, len);
        for (uint8_t i = 0U; i < len; ++i) {
            *hash = fnv1a_update(*hash, ascii_lower(packet[cursor + i]));
        }
        cursor += len;
    }
    return -1;
}

static int parse_dns_packet(
    const uint8_t *packet,
    size_t packet_len,
    bool expect_response,
    struct bpf2socks_dns_packet_info *out) {
    if (packet == NULL || out == NULL || packet_len < DNS_HEADER_SIZE) {
        errno = EINVAL;
        return -1;
    }
    bool is_response = (packet[2] & DNS_QR_RESPONSE) != 0U;
    if (is_response != expect_response) {
        errno = EPROTO;
        return -1;
    }

    memset(out, 0, sizeof(*out));
    out->id = read_be16(packet, 0U);
    uint16_t qdcount = read_be16(packet, 4U);
    if (!expect_response && qdcount == 0U) {
        errno = EPROTO;
        return -1;
    }
    if (qdcount == 0U) return 0;

    uint32_t hash = 2166136261U;
    size_t question_end = 0U;
    if (fingerprint_name(packet, packet_len, DNS_HEADER_SIZE, &question_end, &hash) < 0 ||
        question_end + 4U > packet_len) {
        errno = EPROTO;
        return -1;
    }
    uint16_t qtype = read_be16(packet, question_end);
    uint16_t qclass = read_be16(packet, question_end + 2U);
    hash = fnv1a_update(hash, (uint8_t)(qtype >> 8));
    hash = fnv1a_update(hash, (uint8_t)(qtype & 0xffU));
    hash = fnv1a_update(hash, (uint8_t)(qclass >> 8));
    hash = fnv1a_update(hash, (uint8_t)(qclass & 0xffU));
    out->has_question = true;
    out->question_fingerprint = hash == 0U ? 1U : hash;
    return 0;
}

int bpf2socks_dns_parse_query(
    const uint8_t *packet,
    size_t packet_len,
    struct bpf2socks_dns_packet_info *out) {
    return parse_dns_packet(packet, packet_len, false, out);
}

int bpf2socks_dns_parse_response(
    const uint8_t *packet,
    size_t packet_len,
    struct bpf2socks_dns_packet_info *out) {
    return parse_dns_packet(packet, packet_len, true, out);
}

void bpf2socks_dns_set_id(uint8_t *packet, size_t packet_len, uint16_t id) {
    if (packet == NULL || packet_len < 2U) return;
    packet[0] = (uint8_t)(id >> 8);
    packet[1] = (uint8_t)(id & 0xffU);
}

static size_t lookup_offset(uint32_t channel_index, uint16_t id) {
    return ((size_t)channel_index * 65536U) + id;
}

static void close_tx_reply_fd(struct bpf2socks_dns_transaction *tx) {
    if (tx != NULL && tx->reply_fd >= 0) {
        close(tx->reply_fd);
        tx->reply_fd = -1;
    }
}

static void lru_detach(struct bpf2socks_dns_table *table, size_t index) {
    struct bpf2socks_dns_transaction *tx = &table->transactions[index];
    if (tx->lru_prev != DNS_LRU_NONE) {
        table->transactions[tx->lru_prev].lru_next = tx->lru_next;
    } else if (table->lru_head == index) {
        table->lru_head = tx->lru_next;
    }
    if (tx->lru_next != DNS_LRU_NONE) {
        table->transactions[tx->lru_next].lru_prev = tx->lru_prev;
    } else if (table->lru_tail == index) {
        table->lru_tail = tx->lru_prev;
    }
    tx->lru_prev = DNS_LRU_NONE;
    tx->lru_next = DNS_LRU_NONE;
}

static void lru_attach_tail(struct bpf2socks_dns_table *table, size_t index) {
    struct bpf2socks_dns_transaction *tx = &table->transactions[index];
    tx->lru_prev = table->lru_tail;
    tx->lru_next = DNS_LRU_NONE;
    if (table->lru_tail != DNS_LRU_NONE) {
        table->transactions[table->lru_tail].lru_next = index;
    } else {
        table->lru_head = index;
    }
    table->lru_tail = index;
}

static void release_index(struct bpf2socks_dns_table *table, size_t index) {
    if (table == NULL || index >= table->capacity) return;
    struct bpf2socks_dns_transaction *tx = &table->transactions[index];
    if (!tx->used) return;
    size_t offset = lookup_offset(tx->channel_index, tx->rewritten_id);
    if (table->lookup[offset] == (int32_t)index) table->lookup[offset] = -1;
    lru_detach(table, index);
    close_tx_reply_fd(tx);
    memset(tx, 0, sizeof(*tx));
    tx->reply_fd = -1;
    tx->lru_prev = DNS_LRU_NONE;
    tx->lru_next = DNS_LRU_NONE;
    if (table->count > 0U) --table->count;
}

int bpf2socks_dns_table_init(
    struct bpf2socks_dns_table *table,
    uint32_t channel_count,
    size_t capacity) {
    if (table == NULL || channel_count == 0U || capacity == 0U) {
        errno = EINVAL;
        return -1;
    }
    memset(table, 0, sizeof(*table));
    table->lru_head = DNS_LRU_NONE;
    table->lru_tail = DNS_LRU_NONE;
    table->transactions = calloc(capacity, sizeof(*table->transactions));
    table->lookup = malloc((size_t)channel_count * 65536U * sizeof(*table->lookup));
    table->next_ids = calloc(channel_count, sizeof(*table->next_ids));
    if (table->transactions == NULL || table->lookup == NULL || table->next_ids == NULL) {
        bpf2socks_dns_table_free(table);
        errno = ENOMEM;
        return -1;
    }
    table->capacity = capacity;
    table->channel_count = channel_count;
    for (size_t i = 0; i < (size_t)channel_count * 65536U; ++i) table->lookup[i] = -1;
    for (size_t i = 0; i < capacity; ++i) {
        table->transactions[i].reply_fd = -1;
        table->transactions[i].lru_prev = DNS_LRU_NONE;
        table->transactions[i].lru_next = DNS_LRU_NONE;
    }
    return 0;
}

void bpf2socks_dns_table_free(struct bpf2socks_dns_table *table) {
    if (table == NULL) return;
    if (table->transactions != NULL) {
        for (size_t i = 0; i < table->capacity; ++i) close_tx_reply_fd(&table->transactions[i]);
    }
    free(table->transactions);
    free(table->lookup);
    free(table->next_ids);
    memset(table, 0, sizeof(*table));
    table->lru_head = DNS_LRU_NONE;
    table->lru_tail = DNS_LRU_NONE;
}

static size_t find_free_slot(struct bpf2socks_dns_table *table) {
    for (size_t i = 0; i < table->capacity; ++i) {
        if (!table->transactions[i].used) return i;
    }
    return DNS_FREE_INDEX;
}

static bool allocate_id(struct bpf2socks_dns_table *table, uint32_t channel, uint16_t *out_id) {
    uint16_t start = table->next_ids[channel];
    uint16_t id = start;
    do {
        if (table->lookup[lookup_offset(channel, id)] < 0) {
            *out_id = id;
            table->next_ids[channel] = (uint16_t)(id + 1U);
            return true;
        }
        id = (uint16_t)(id + 1U);
    } while (id != start);
    return false;
}

struct bpf2socks_dns_transaction *bpf2socks_dns_table_alloc(
    struct bpf2socks_dns_table *table,
    uint32_t preferred_channel,
    uint16_t original_id,
    uint32_t question_fingerprint,
    uint64_t now_ms) {
    if (table == NULL || table->transactions == NULL || table->lookup == NULL ||
        preferred_channel >= table->channel_count) {
        errno = EINVAL;
        return NULL;
    }
    size_t slot = find_free_slot(table);
    if (slot == DNS_FREE_INDEX) {
        if (table->lru_head == DNS_LRU_NONE) {
            errno = ENOMEM;
            return NULL;
        }
        slot = table->lru_head;
        release_index(table, slot);
    }

    uint16_t rewritten_id = 0U;
    uint32_t selected_channel = preferred_channel;
    for (uint32_t attempt = 0U; attempt < table->channel_count; ++attempt) {
        uint32_t channel = (preferred_channel + attempt) % table->channel_count;
        if (allocate_id(table, channel, &rewritten_id)) {
            selected_channel = channel;
            goto found_id;
        }
    }
    errno = ENOSPC;
    return NULL;

found_id:
    ;
    struct bpf2socks_dns_transaction *tx = &table->transactions[slot];
    memset(tx, 0, sizeof(*tx));
    tx->used = true;
    tx->channel_index = selected_channel;
    tx->rewritten_id = rewritten_id;
    tx->original_id = original_id;
    tx->question_fingerprint = question_fingerprint;
    tx->sent_at_ms = now_ms;
    tx->reply_fd = -1;
    tx->lru_prev = DNS_LRU_NONE;
    tx->lru_next = DNS_LRU_NONE;
    table->lookup[lookup_offset(selected_channel, rewritten_id)] = (int32_t)slot;
    lru_attach_tail(table, slot);
    ++table->count;
    return tx;
}

struct bpf2socks_dns_transaction *bpf2socks_dns_table_find(
    struct bpf2socks_dns_table *table,
    uint32_t channel_index,
    uint16_t rewritten_id) {
    if (table == NULL || table->lookup == NULL || channel_index >= table->channel_count) return NULL;
    int32_t index = table->lookup[lookup_offset(channel_index, rewritten_id)];
    if (index < 0 || (size_t)index >= table->capacity) return NULL;
    struct bpf2socks_dns_transaction *tx = &table->transactions[index];
    return tx->used ? tx : NULL;
}

void bpf2socks_dns_table_release(
    struct bpf2socks_dns_table *table,
    struct bpf2socks_dns_transaction *tx) {
    if (table == NULL || tx == NULL || table->transactions == NULL) return;
    size_t index = (size_t)(tx - table->transactions);
    if (index >= table->capacity) return;
    release_index(table, index);
}

size_t bpf2socks_dns_table_expire(
    struct bpf2socks_dns_table *table,
    uint64_t now_ms,
    uint64_t timeout_ms,
    size_t max_expire) {
    if (table == NULL || table->transactions == NULL || max_expire == 0U) return 0U;
    size_t expired = 0U;
    while (table->lru_head != DNS_LRU_NONE && expired < max_expire) {
        size_t index = table->lru_head;
        struct bpf2socks_dns_transaction *tx = &table->transactions[index];
        if (!tx->used || now_ms < tx->sent_at_ms || now_ms - tx->sent_at_ms < timeout_ms) break;
        release_index(table, index);
        ++expired;
    }
    return expired;
}

size_t bpf2socks_dns_table_count(const struct bpf2socks_dns_table *table) {
    return table == NULL ? 0U : table->count;
}
