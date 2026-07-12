// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "asteriskd.h"

#include <arpa/inet.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

static bool is_valid_event_action(enum asteriskd_event_action action) {
    return action == ASTERISKD_EVENT_ADDED || action == ASTERISKD_EVENT_REMOVED || action == ASTERISKD_EVENT_UPDATED;
}

static bool events_match(const struct asteriskd_network_event *left, const struct asteriskd_network_event *right) {
    return left->is_address == right->is_address &&
        left->action == right->action &&
        left->family == right->family &&
        strcmp(left->interface_name, right->interface_name) == 0 &&
        strcmp(left->address, right->address) == 0;
}

static void record_event(struct asteriskd_event_batch *batch, const struct asteriskd_network_event *event) {
    if (batch == NULL || event == NULL || event->interface_name[0] == '\0') return;
    for (size_t index = 0U; index < batch->count; ++index) {
        if (events_match(&batch->events[index], event)) return;
    }
    if (batch->count >= ASTERISKD_MAX_NETWORK_EVENTS) {
        batch->truncated = true;
        return;
    }
    batch->events[batch->count++] = *event;
}

void asteriskd_event_batch_init(struct asteriskd_event_batch *batch) {
    if (batch != NULL) memset(batch, 0, sizeof(*batch));
}

void asteriskd_event_batch_record_link(
    struct asteriskd_event_batch *batch,
    enum asteriskd_event_action action,
    const char *interface_name) {
    if (!is_valid_event_action(action) || interface_name == NULL) return;
    struct asteriskd_network_event event;
    memset(&event, 0, sizeof(event));
    event.action = action;
    (void)snprintf(event.interface_name, sizeof(event.interface_name), "%s", interface_name);
    record_event(batch, &event);
}

void asteriskd_event_batch_record_address(
    struct asteriskd_event_batch *batch,
    enum asteriskd_event_action action,
    int family,
    const char *interface_name,
    const char *address) {
    if (!is_valid_event_action(action) || (family != AF_INET && family != AF_INET6) ||
        interface_name == NULL || address == NULL) {
        return;
    }
    struct asteriskd_network_event event;
    memset(&event, 0, sizeof(event));
    event.is_address = true;
    event.action = action;
    event.family = family;
    (void)snprintf(event.interface_name, sizeof(event.interface_name), "%s", interface_name);
    (void)snprintf(event.address, sizeof(event.address), "%s", address);
    record_event(batch, &event);
}

bool asteriskd_event_batch_has_link_event(const struct asteriskd_event_batch *batch) {
    if (batch == NULL) return false;
    for (size_t index = 0U; index < batch->count; ++index) {
        if (!batch->events[index].is_address) return true;
    }
    return false;
}

bool asteriskd_event_batch_has_hotspot_interface_event(
    const struct asteriskd_event_batch *batch,
    const struct asteriskd_config *config) {
    if (batch == NULL || config == NULL) return false;
    for (size_t event_index = 0U; event_index < batch->count; ++event_index) {
        for (size_t prefix_index = 0U; prefix_index < config->hotspot_interface_prefix_count; ++prefix_index) {
            if (asteriskd_interface_matches_prefix(
                    batch->events[event_index].interface_name,
                    config->hotspot_interface_prefixes[prefix_index])) {
                return true;
            }
        }
    }
    return false;
}

static const char *action_name(enum asteriskd_event_action action) {
    switch (action) {
        case ASTERISKD_EVENT_ADDED: return "added";
        case ASTERISKD_EVENT_REMOVED: return "removed";
        case ASTERISKD_EVENT_UPDATED: return "updated";
    }
    return "unknown";
}

static void append_text(char *output, size_t output_size, size_t *used, const char *format, ...) {
    if (output == NULL || output_size == 0U || *used >= output_size - 1U) return;
    va_list arguments;
    va_start(arguments, format);
    int count = vsnprintf(output + *used, output_size - *used, format, arguments);
    va_end(arguments);
    if (count < 0) return;
    size_t written = (size_t)count;
    *used = written >= output_size - *used ? output_size - 1U : *used + written;
}

void asteriskd_format_event_batch(const struct asteriskd_event_batch *batch, char *output, size_t output_size) {
    if (output == NULL || output_size == 0U) return;
    output[0] = '\0';
    if (batch == NULL || (batch->count == 0U && !batch->truncated)) return;
    size_t used = 0U;
    append_text(output, output_size, &used, "network change: ");
    for (size_t index = 0U; index < batch->count; ++index) {
        const struct asteriskd_network_event *event = &batch->events[index];
        if (index > 0U) append_text(output, output_size, &used, "; ");
        if (!event->is_address) {
            append_text(output, output_size, &used, "link=%s(%s)", event->interface_name, action_name(event->action));
            continue;
        }
        const char *family = event->family == AF_INET6 ? "ipv6" : "ipv4";
        const char *action = event->action == ASTERISKD_EVENT_REMOVED ? "-" : "+";
        append_text(output, output_size, &used, "%s=%s:%s%s", family, event->interface_name, action, event->address);
    }
    if (batch->truncated) {
        if (batch->count > 0U) append_text(output, output_size, &used, "; ");
        append_text(output, output_size, &used, "events-truncated");
    }
}

void asteriskd_log_event_batch(struct asteriskd_state *state, const struct asteriskd_event_batch *batch) {
    char summary[2048];
    asteriskd_format_event_batch(batch, summary, sizeof(summary));
    if (summary[0] != '\0') asteriskd_log(state, "%s", summary);
}


