// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#ifndef ASTERISKD_H
#define ASTERISKD_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include <stdio.h>

#define ASTERISKD_CONFIG_VERSION 1U
#define ASTERISKD_MAX_PATH 4096U
#define ASTERISKD_MAX_INTERFACES 64U
#define ASTERISKD_MAX_INTERFACE_NAME 64U
#define ASTERISKD_MAX_ADDRESSES 256U
#define ASTERISKD_MAX_CHAIN_NAME 29U
#define ASTERISKD_MAX_NETWORK_EVENTS 16U
// Cellular modems can emit a short optimistic/tentative IPv6 address sequence
// before withdrawing it. Wait for that sequence to settle before changing rules.
#define ASTERISKD_SYNC_DEBOUNCE_MILLIS 1500

enum asteriskd_mode {
    ASTERISKD_MODE_TPROXY,
    ASTERISKD_MODE_TUN,
    ASTERISKD_MODE_TUN2SOCKS,
    ASTERISKD_MODE_BPF2SOCKS,
};

struct asteriskd_bypass_target {
    bool enabled;
    char anchor_chain[ASTERISKD_MAX_CHAIN_NAME];
    char slot_a_chain[ASTERISKD_MAX_CHAIN_NAME];
    char slot_b_chain[ASTERISKD_MAX_CHAIN_NAME];
};

struct asteriskd_bpf_local_maps {
    bool enabled;
    char ipv4_path[ASTERISKD_MAX_PATH];
    char ipv6_path[ASTERISKD_MAX_PATH];
};

struct asteriskd_config {
    uint32_t version;
    enum asteriskd_mode mode;
    bool enable_ipv6;
    bool disable_system_ipv6;
    char data_directory[ASTERISKD_MAX_PATH];
    char stop_script_path[ASTERISKD_MAX_PATH];
    char state_path[ASTERISKD_MAX_PATH];
    char ignored_interfaces[ASTERISKD_MAX_INTERFACES][ASTERISKD_MAX_INTERFACE_NAME];
    size_t ignored_interface_count;
    char virtual_interfaces[ASTERISKD_MAX_INTERFACES][ASTERISKD_MAX_INTERFACE_NAME];
    size_t virtual_interface_count;
    char hotspot_interface_prefixes[ASTERISKD_MAX_INTERFACES][ASTERISKD_MAX_INTERFACE_NAME];
    size_t hotspot_interface_prefix_count;
    struct asteriskd_bypass_target ipv4_bypass;
    struct asteriskd_bypass_target ipv6_bypass;
    struct asteriskd_bpf_local_maps bpf_local_maps;
};

struct asteriskd_address_set {
    int family;
    char values[ASTERISKD_MAX_ADDRESSES][64];
    size_t count;
};

enum asteriskd_event_action {
    ASTERISKD_EVENT_ADDED,
    ASTERISKD_EVENT_REMOVED,
    ASTERISKD_EVENT_UPDATED,
};

struct asteriskd_network_event {
    bool is_address;
    enum asteriskd_event_action action;
    int family;
    char interface_name[ASTERISKD_MAX_INTERFACE_NAME];
    char address[64];
};

struct asteriskd_event_batch {
    struct asteriskd_network_event events[ASTERISKD_MAX_NETWORK_EVENTS];
    size_t count;
    bool truncated;
};

struct asteriskd_ipv6_state_entry {
    char interface_name[ASTERISKD_MAX_INTERFACE_NAME];
    char original_value;
};

struct asteriskd_state {
    char pid_path[ASTERISKD_MAX_PATH];
    char log_path[ASTERISKD_MAX_PATH];
    char ready_path[ASTERISKD_MAX_PATH];
    FILE *log_file;
    struct asteriskd_ipv6_state_entry ipv6_entries[ASTERISKD_MAX_INTERFACES];
    size_t ipv6_entry_count;
    int active_ipv4_slot;
    int active_ipv6_slot;
    struct asteriskd_address_set synchronized_ipv4_addresses;
    struct asteriskd_address_set synchronized_ipv6_addresses;
    bool has_synchronized_addresses;
};

int asteriskd_load_config(const char *path, struct asteriskd_config *out, char *message, size_t message_size);

void asteriskd_state_init(struct asteriskd_state *state, const char *pid_path, const char *log_path, const char *data_directory);
void asteriskd_open_log(struct asteriskd_state *state);
void asteriskd_close_log(struct asteriskd_state *state);
void asteriskd_log(struct asteriskd_state *state, const char *format, ...);

void asteriskd_event_batch_init(struct asteriskd_event_batch *batch);
void asteriskd_event_batch_record_link(
    struct asteriskd_event_batch *batch,
    enum asteriskd_event_action action,
    const char *interface_name);
void asteriskd_event_batch_record_address(
    struct asteriskd_event_batch *batch,
    enum asteriskd_event_action action,
    int family,
    const char *interface_name,
    const char *address);
bool asteriskd_event_batch_has_link_event(const struct asteriskd_event_batch *batch);
bool asteriskd_event_batch_has_hotspot_interface_event(
    const struct asteriskd_event_batch *batch,
    const struct asteriskd_config *config);
void asteriskd_format_event_batch(const struct asteriskd_event_batch *batch, char *output, size_t output_size);
void asteriskd_log_event_batch(struct asteriskd_state *state, const struct asteriskd_event_batch *batch);

int asteriskd_prepare(const struct asteriskd_config *config, struct asteriskd_state *state);
int asteriskd_run(const struct asteriskd_config *config, struct asteriskd_state *state);
void asteriskd_restore_ipv6(const struct asteriskd_config *config, struct asteriskd_state *state);
int asteriskd_disable_system_ipv6_for_sync(const struct asteriskd_config *config, struct asteriskd_state *state);
_Noreturn void asteriskd_fail_stop(const struct asteriskd_config *config, struct asteriskd_state *state, const char *step);

int asteriskd_collect_local_addresses(const struct asteriskd_config *config, int family, struct asteriskd_address_set *out);
int asteriskd_prepare_iptables_bypass(const struct asteriskd_config *config);
int asteriskd_replace_iptables_bypass(
    const struct asteriskd_bypass_target *target,
    int family,
    const struct asteriskd_address_set *addresses,
    int *active_slot);
int asteriskd_replace_lpm4_map(const char *pin_path, const struct asteriskd_address_set *addresses);
int asteriskd_replace_lpm6_map(const char *pin_path, const struct asteriskd_address_set *addresses);
int asteriskd_clear_hotspot_ipv6_tc_offload(const struct asteriskd_config *config);
int asteriskd_sync_all(
    const struct asteriskd_config *config,
    struct asteriskd_state *state,
    bool synchronize_ipv6_interfaces,
    bool synchronize_hotspot_interfaces,
    bool *addresses_changed);

bool asteriskd_should_track_interface(const struct asteriskd_config *config, const char *interface_name);
bool asteriskd_interface_matches_prefix(const char *interface_name, const char *prefix);
uint32_t asteriskd_netlink_groups(const struct asteriskd_config *config);

#endif



