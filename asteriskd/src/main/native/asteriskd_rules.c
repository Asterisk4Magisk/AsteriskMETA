// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "asteriskd.h"

#include <arpa/inet.h>
#include <dirent.h>
#include <errno.h>
#include <ifaddrs.h>
#include <net/if.h>
#include <netinet/in.h>
#include <stdbool.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/wait.h>
#include <unistd.h>

static int run_command(char *const arguments[], char *output, size_t output_size) {
    int pipe_fd[2] = {-1, -1};
    if (output != NULL && pipe(pipe_fd) != 0) return -1;
    pid_t child = fork();
    if (child < 0) {
        if (pipe_fd[0] >= 0) {
            (void)close(pipe_fd[0]);
            (void)close(pipe_fd[1]);
        }
        return -1;
    }
    if (child == 0) {
        if (output != NULL) {
            (void)close(pipe_fd[0]);
            (void)dup2(pipe_fd[1], STDOUT_FILENO);
            (void)close(pipe_fd[1]);
        }
        execvp(arguments[0], arguments);
        _exit(127);
    }
    if (output != NULL) {
        (void)close(pipe_fd[1]);
        size_t used = 0U;
        char buffer[1024];
        while (true) {
            ssize_t count = read(pipe_fd[0], buffer, sizeof(buffer));
            if (count <= 0) break;
            size_t available = used + 1U < output_size ? output_size - used - 1U : 0U;
            size_t copied = (size_t)count < available ? (size_t)count : available;
            if (copied > 0U) {
                memcpy(output + used, buffer, copied);
                used += copied;
            }
        }
        if (output_size > 0U) output[used] = '\0';
        (void)close(pipe_fd[0]);
    }
    int status = 0;
    if (waitpid(child, &status, 0) != child || !WIFEXITED(status) || WEXITSTATUS(status) != 0) {
        errno = EIO;
        return -1;
    }
    return 0;
}

static const char *iptables_program(int family) {
    return family == AF_INET6 ? "ip6tables" : "iptables";
}

static int iptables_chain_exists(int family, const char *chain) {
    char *arguments[] = {
        (char *)iptables_program(family), "-w", "100", "-t", "mangle", "-S", (char *)chain, NULL,
    };
    return run_command(arguments, NULL, 0U);
}

static int iptables_ensure_chain(int family, const char *chain) {
    if (iptables_chain_exists(family, chain) == 0) return 0;
    char *arguments[] = {
        (char *)iptables_program(family), "-w", "100", "-t", "mangle", "-N", (char *)chain, NULL,
    };
    return run_command(arguments, NULL, 0U);
}

static int iptables_flush_chain(int family, const char *chain) {
    char *arguments[] = {
        (char *)iptables_program(family), "-w", "100", "-t", "mangle", "-F", (char *)chain, NULL,
    };
    return run_command(arguments, NULL, 0U);
}

static int iptables_append_jump(int family, const char *chain, const char *target) {
    char *arguments[] = {
        (char *)iptables_program(family), "-w", "100", "-t", "mangle", "-A", (char *)chain, "-j", (char *)target, NULL,
    };
    return run_command(arguments, NULL, 0U);
}

static int iptables_replace_jump(int family, const char *chain, const char *target) {
    char *arguments[] = {
        (char *)iptables_program(family), "-w", "100", "-t", "mangle", "-R", (char *)chain, "1", "-j", (char *)target, NULL,
    };
    return run_command(arguments, NULL, 0U);
}

static int iptables_append_return(int family, const char *chain, const char *address) {
    char cidr[80];
    int prefix = family == AF_INET6 ? 128 : 32;
    if (snprintf(cidr, sizeof(cidr), "%s/%d", address, prefix) <= 0) {
        errno = EINVAL;
        return -1;
    }
    char *arguments[] = {
        (char *)iptables_program(family), "-w", "100", "-t", "mangle", "-A", (char *)chain,
        "-d", cidr, "-j", "RETURN", NULL,
    };
    return run_command(arguments, NULL, 0U);
}

static int prepare_target(int family, const struct asteriskd_bypass_target *target) {
    if (!target->enabled) return 0;
    if (iptables_ensure_chain(family, target->anchor_chain) != 0 ||
        iptables_ensure_chain(family, target->slot_a_chain) != 0 ||
        iptables_ensure_chain(family, target->slot_b_chain) != 0 ||
        iptables_flush_chain(family, target->anchor_chain) != 0 ||
        iptables_flush_chain(family, target->slot_a_chain) != 0 ||
        iptables_flush_chain(family, target->slot_b_chain) != 0 ||
        iptables_append_jump(family, target->anchor_chain, target->slot_a_chain) != 0) {
        return -1;
    }
    return 0;
}

int asteriskd_prepare_iptables_bypass(const struct asteriskd_config *config) {
    if (prepare_target(AF_INET, &config->ipv4_bypass) != 0) return -1;
    if (config->enable_ipv6 && prepare_target(AF_INET6, &config->ipv6_bypass) != 0) return -1;
    return 0;
}

static bool address_already_added(const struct asteriskd_address_set *set, const char *address) {
    for (size_t index = 0U; index < set->count; ++index) {
        if (strcmp(set->values[index], address) == 0) return true;
    }
    return false;
}

static bool address_sets_match(
    const struct asteriskd_address_set *left,
    const struct asteriskd_address_set *right) {
    if (left->family != right->family || left->count != right->count) return false;
    for (size_t index = 0U; index < left->count; ++index) {
        if (!address_already_added(right, left->values[index])) return false;
    }
    return true;
}

int asteriskd_collect_local_addresses(
    const struct asteriskd_config *config,
    int family,
    struct asteriskd_address_set *out) {
    memset(out, 0, sizeof(*out));
    out->family = family;
    struct ifaddrs *interfaces = NULL;
    if (getifaddrs(&interfaces) != 0) return -1;
    int result = 0;
    for (const struct ifaddrs *entry = interfaces; entry != NULL; entry = entry->ifa_next) {
        if (entry->ifa_addr == NULL || entry->ifa_addr->sa_family != family ||
            !asteriskd_should_track_interface(config, entry->ifa_name)) {
            continue;
        }
        char address[64];
        const void *source = NULL;
        if (family == AF_INET) {
            const struct sockaddr_in *socket_address = (const struct sockaddr_in *)entry->ifa_addr;
            if (socket_address->sin_addr.s_addr == INADDR_ANY || IN_MULTICAST(ntohl(socket_address->sin_addr.s_addr))) continue;
            source = &socket_address->sin_addr;
        } else {
            const struct sockaddr_in6 *socket_address = (const struct sockaddr_in6 *)entry->ifa_addr;
            if (IN6_IS_ADDR_UNSPECIFIED(&socket_address->sin6_addr) || IN6_IS_ADDR_MULTICAST(&socket_address->sin6_addr)) continue;
            source = &socket_address->sin6_addr;
        }
        if (inet_ntop(family, source, address, sizeof(address)) == NULL) {
            result = -1;
            break;
        }
        if (!address_already_added(out, address)) {
            if (out->count >= ASTERISKD_MAX_ADDRESSES) {
                errno = ENOSPC;
                result = -1;
                break;
            }
            (void)snprintf(out->values[out->count++], sizeof(out->values[0]), "%s", address);
        }
    }
    freeifaddrs(interfaces);
    return result;
}

int asteriskd_replace_iptables_bypass(
    const struct asteriskd_bypass_target *target,
    int family,
    const struct asteriskd_address_set *addresses,
    int *active_slot) {
    if (!target->enabled || active_slot == NULL || (*active_slot != 0 && *active_slot != 1)) {
        errno = EINVAL;
        return -1;
    }
    const char *old_chain = *active_slot == 0 ? target->slot_a_chain : target->slot_b_chain;
    const char *next_chain = *active_slot == 0 ? target->slot_b_chain : target->slot_a_chain;
    if (iptables_flush_chain(family, next_chain) != 0) return -1;
    for (size_t index = 0U; index < addresses->count; ++index) {
        if (iptables_append_return(family, next_chain, addresses->values[index]) != 0) return -1;
    }
    if (iptables_replace_jump(family, target->anchor_chain, next_chain) != 0 ||
        iptables_flush_chain(family, old_chain) != 0) {
        return -1;
    }
    *active_slot = *active_slot == 0 ? 1 : 0;
    return 0;
}

int asteriskd_clear_hotspot_ipv6_tc_offload(const struct asteriskd_config *config) {
    if (!config->enable_ipv6 || config->hotspot_interface_prefix_count == 0U) return 0;
    DIR *directory = opendir("/sys/class/net");
    if (directory == NULL) return -1;
    int result = 0;
    struct dirent *entry;
    while ((entry = readdir(directory)) != NULL) {
        bool matched = false;
        for (size_t index = 0U; index < config->hotspot_interface_prefix_count; ++index) {
            if (asteriskd_interface_matches_prefix(entry->d_name, config->hotspot_interface_prefixes[index])) {
                matched = true;
                break;
            }
        }
        if (!matched) continue;
        char output[8192];
        char *show_arguments[] = {"tc", "filter", "show", "dev", entry->d_name, "ingress", "protocol", "ipv6", NULL};
        if (run_command(show_arguments, output, sizeof(output)) != 0) {
            result = -1;
            break;
        }
        if (strstr(output, "prog_offload_schedcls_tether_") == NULL) continue;
        char *delete_arguments[] = {
            "tc", "filter", "del", "dev", entry->d_name, "ingress", "protocol", "ipv6", "pref", "2", NULL,
        };
        if (run_command(delete_arguments, NULL, 0U) != 0) {
            result = -1;
            break;
        }
    }
    (void)closedir(directory);
    return result;
}

int asteriskd_sync_all(
    const struct asteriskd_config *config,
    struct asteriskd_state *state,
    bool synchronize_ipv6_interfaces,
    bool synchronize_hotspot_interfaces,
    bool *addresses_changed) {
    if (addresses_changed == NULL) {
        errno = EINVAL;
        return -1;
    }
    *addresses_changed = false;
    if (config->disable_system_ipv6 && synchronize_ipv6_interfaces) {
        if (asteriskd_disable_system_ipv6_for_sync(config, state) != 0) return -1;
    }
    struct asteriskd_address_set ipv4_addresses;
    if (asteriskd_collect_local_addresses(config, AF_INET, &ipv4_addresses) != 0) return -1;
    struct asteriskd_address_set ipv6_addresses;
    memset(&ipv6_addresses, 0, sizeof(ipv6_addresses));
    ipv6_addresses.family = AF_INET6;
    if (config->enable_ipv6) {
        if (asteriskd_collect_local_addresses(config, AF_INET6, &ipv6_addresses) != 0) {
            return -1;
        }
    }
    bool changed = !state->has_synchronized_addresses ||
        !address_sets_match(&state->synchronized_ipv4_addresses, &ipv4_addresses) ||
        !address_sets_match(&state->synchronized_ipv6_addresses, &ipv6_addresses);
    if (changed) {
        if ((config->ipv4_bypass.enabled &&
             asteriskd_replace_iptables_bypass(&config->ipv4_bypass, AF_INET, &ipv4_addresses, &state->active_ipv4_slot) != 0) ||
            (config->bpf_local_maps.enabled &&
             asteriskd_replace_lpm4_map(config->bpf_local_maps.ipv4_path, &ipv4_addresses) != 0) ||
            (config->enable_ipv6 && config->ipv6_bypass.enabled &&
             asteriskd_replace_iptables_bypass(&config->ipv6_bypass, AF_INET6, &ipv6_addresses, &state->active_ipv6_slot) != 0) ||
            (config->bpf_local_maps.enabled &&
             asteriskd_replace_lpm6_map(config->bpf_local_maps.ipv6_path, &ipv6_addresses) != 0)) {
            return -1;
        }
        state->synchronized_ipv4_addresses = ipv4_addresses;
        state->synchronized_ipv6_addresses = ipv6_addresses;
        state->has_synchronized_addresses = true;
        *addresses_changed = true;
        asteriskd_log(state, "synchronized local addresses: ipv4=%zu ipv6=%zu", ipv4_addresses.count, ipv6_addresses.count);
    }
    if (config->enable_ipv6 && synchronize_hotspot_interfaces &&
        asteriskd_clear_hotspot_ipv6_tc_offload(config) != 0) {
        return -1;
    }
    return 0;
}


