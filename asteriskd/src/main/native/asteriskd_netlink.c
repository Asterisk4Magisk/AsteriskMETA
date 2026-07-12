// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#define _GNU_SOURCE

#include "asteriskd.h"

#include <arpa/inet.h>
#include <dirent.h>
#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <linux/netlink.h>
#include <linux/rtnetlink.h>
#include <net/if.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

#ifndef RTMGRP_IPV6_IFADDR
#define RTMGRP_IPV6_IFADDR 0x100U
#endif

#define ASTERISKD_IPV6_CONF_DIR "/proc/sys/net/ipv6/conf"

static volatile sig_atomic_t stop_requested = 0;

static void on_signal(int signal_number) {
    (void)signal_number;
    stop_requested = 1;
}

static long long monotonic_millis(void) {
    struct timespec value;
    if (clock_gettime(CLOCK_MONOTONIC, &value) != 0) return -1LL;
    return (long long)value.tv_sec * 1000LL + value.tv_nsec / 1000000LL;
}

static bool is_system_ipv6_interface(const char *interface_name) {
    return interface_name != NULL && interface_name[0] != '\0' &&
        strcmp(interface_name, ".") != 0 && strcmp(interface_name, "..") != 0 &&
        strcmp(interface_name, "all") != 0 && strcmp(interface_name, "lo") != 0 &&
        strchr(interface_name, '/') == NULL;
}

static int sysctl_path(const char *interface_name, char *path, size_t path_size) {
    int count = snprintf(path, path_size, "%s/%s/disable_ipv6", ASTERISKD_IPV6_CONF_DIR, interface_name);
    if (count < 0 || (size_t)count >= path_size) {
        errno = ENAMETOOLONG;
        return -1;
    }
    return 0;
}

static int read_disable_ipv6(const char *interface_name, char *out) {
    char path[PATH_MAX];
    if (sysctl_path(interface_name, path, sizeof(path)) != 0) return -1;
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    char buffer[8];
    ssize_t count = read(fd, buffer, sizeof(buffer));
    int saved_errno = errno;
    (void)close(fd);
    errno = saved_errno;
    if (count <= 0) return -1;
    for (ssize_t index = 0; index < count; ++index) {
        if (buffer[index] == '0' || buffer[index] == '1') {
            *out = buffer[index];
            return 0;
        }
    }
    errno = EINVAL;
    return -1;
}

static int write_disable_ipv6(const char *interface_name, char value) {
    char path[PATH_MAX];
    if (sysctl_path(interface_name, path, sizeof(path)) != 0) return -1;
    int fd = open(path, O_WRONLY | O_CLOEXEC);
    if (fd < 0) return -1;
    char buffer[] = {value, '\n'};
    ssize_t count = write(fd, buffer, sizeof(buffer));
    int saved_errno = errno;
    (void)close(fd);
    errno = saved_errno;
    if (count == (ssize_t)sizeof(buffer)) return 0;
    if (count >= 0) errno = EIO;
    return -1;
}

static int persist_ipv6_state(const struct asteriskd_config *config, const struct asteriskd_state *state) {
    FILE *file = fopen(config->state_path, "w");
    if (file == NULL) return -1;
    for (size_t index = 0U; index < state->ipv6_entry_count; ++index) {
        if (fprintf(file, "%s %c\n", state->ipv6_entries[index].interface_name, state->ipv6_entries[index].original_value) < 0) {
            (void)fclose(file);
            return -1;
        }
    }
    if (fflush(file) != 0 || fsync(fileno(file)) != 0 || fclose(file) != 0) return -1;
    return 0;
}

static int add_original_ipv6_value(
    const struct asteriskd_config *config,
    struct asteriskd_state *state,
    const char *interface_name,
    char *out) {
    for (size_t index = 0U; index < state->ipv6_entry_count; ++index) {
        if (strcmp(state->ipv6_entries[index].interface_name, interface_name) == 0) {
            *out = state->ipv6_entries[index].original_value;
            return 0;
        }
    }
    if (state->ipv6_entry_count >= ASTERISKD_MAX_INTERFACES ||
        read_disable_ipv6(interface_name, out) != 0) {
        return -1;
    }
    struct asteriskd_ipv6_state_entry *entry = &state->ipv6_entries[state->ipv6_entry_count++];
    (void)snprintf(entry->interface_name, sizeof(entry->interface_name), "%s", interface_name);
    entry->original_value = *out;
    if (persist_ipv6_state(config, state) != 0) return -1;
    return 0;
}

static int disable_ipv6_interface(
    const struct asteriskd_config *config,
    struct asteriskd_state *state,
    const char *interface_name) {
    char original = '\0';
    if (add_original_ipv6_value(config, state, interface_name, &original) != 0) return -1;
    return original == '1' ? 0 : write_disable_ipv6(interface_name, '1');
}

int asteriskd_disable_system_ipv6_for_sync(const struct asteriskd_config *config, struct asteriskd_state *state) {
    if (!config->disable_system_ipv6) return 0;
    if (disable_ipv6_interface(config, state, "default") != 0) return -1;
    DIR *directory = opendir(ASTERISKD_IPV6_CONF_DIR);
    if (directory == NULL) return -1;
    int result = 0;
    struct dirent *entry;
    while ((entry = readdir(directory)) != NULL) {
        if (!is_system_ipv6_interface(entry->d_name)) continue;
        if (disable_ipv6_interface(config, state, entry->d_name) != 0) {
            result = -1;
            break;
        }
    }
    (void)closedir(directory);
    return result;
}

static int restore_stale_ipv6_state(const struct asteriskd_config *config) {
    FILE *file = fopen(config->state_path, "r");
    if (file == NULL) return errno == ENOENT ? 0 : -1;
    char interface_name[ASTERISKD_MAX_INTERFACE_NAME];
    char value = '\0';
    int result = 0;
    while (fscanf(file, "%63s %c", interface_name, &value) == 2) {
        char path[PATH_MAX];
        if (!is_system_ipv6_interface(interface_name) || (value != '0' && value != '1') ||
            sysctl_path(interface_name, path, sizeof(path)) != 0) {
            result = -1;
            break;
        }
        if (access(path, F_OK) == 0 && write_disable_ipv6(interface_name, value) != 0) {
            result = -1;
            break;
        }
    }
    (void)fclose(file);
    if (result == 0) (void)unlink(config->state_path);
    return result;
}

void asteriskd_restore_ipv6(const struct asteriskd_config *config, struct asteriskd_state *state) {
    if (!config->disable_system_ipv6) return;
    for (size_t index = 0U; index < state->ipv6_entry_count; ++index) {
        const char *interface_name = state->ipv6_entries[index].interface_name;
        char path[PATH_MAX];
        if (sysctl_path(interface_name, path, sizeof(path)) == 0 && access(path, F_OK) == 0 &&
            write_disable_ipv6(interface_name, state->ipv6_entries[index].original_value) != 0) {
            asteriskd_log(state, "failed to restore IPv6 for %s: %s", interface_name, strerror(errno));
        }
    }
    state->ipv6_entry_count = 0U;
    (void)unlink(config->state_path);
}

bool asteriskd_interface_matches_prefix(const char *interface_name, const char *prefix) {
    size_t length = strlen(prefix);
    if (length == 0U) return false;
    if (prefix[length - 1U] == '+') {
        return length > 1U && strncmp(interface_name, prefix, length - 1U) == 0;
    }
    return strcmp(interface_name, prefix) == 0;
}

bool asteriskd_should_track_interface(const struct asteriskd_config *config, const char *interface_name) {
    if (!is_system_ipv6_interface(interface_name)) return false;
    for (size_t index = 0U; index < config->ignored_interface_count; ++index) {
        if (strcmp(config->ignored_interfaces[index], interface_name) == 0) return false;
    }
    for (size_t index = 0U; index < config->virtual_interface_count; ++index) {
        if (strcmp(config->virtual_interfaces[index], interface_name) == 0) return false;
    }
    return true;
}

uint32_t asteriskd_netlink_groups(const struct asteriskd_config *config) {
    // Route netlink cannot subscribe by interface prefix. Avoid the broad link
    // multicast group entirely when neither IPv6 disabling nor hotspot TC work
    // needs interface lifecycle events.
    uint32_t groups = RTMGRP_IPV4_IFADDR;
    if (config->enable_ipv6 || config->disable_system_ipv6) groups |= RTMGRP_IPV6_IFADDR;
    if (config->disable_system_ipv6 ||
        (config->enable_ipv6 && config->hotspot_interface_prefix_count > 0U)) {
        groups |= RTMGRP_LINK;
    }
    return groups;
}

static int open_netlink_socket(const struct asteriskd_config *config) {
    int fd = socket(AF_NETLINK, SOCK_RAW | SOCK_CLOEXEC, NETLINK_ROUTE);
    if (fd < 0) return -1;
    struct sockaddr_nl address;
    memset(&address, 0, sizeof(address));
    address.nl_family = AF_NETLINK;
    address.nl_groups = asteriskd_netlink_groups(config);
    if (bind(fd, (const struct sockaddr *)&address, sizeof(address)) != 0) {
        int saved_errno = errno;
        (void)close(fd);
        errno = saved_errno;
        return -1;
    }
    return fd;
}

static void record_link_message(
    const struct asteriskd_config *config,
    const struct nlmsghdr *message,
    struct asteriskd_event_batch *events) {
    if (message->nlmsg_len < NLMSG_LENGTH(sizeof(struct ifinfomsg))) return;
    const struct ifinfomsg *link = (const struct ifinfomsg *)NLMSG_DATA(message);
    char interface_name[ASTERISKD_MAX_INTERFACE_NAME];
    if (if_indextoname((unsigned int)link->ifi_index, interface_name) == NULL ||
        !asteriskd_should_track_interface(config, interface_name)) {
        return;
    }
    bool relevant = config->disable_system_ipv6;
    for (size_t index = 0U; !relevant && index < config->hotspot_interface_prefix_count; ++index) {
        relevant = asteriskd_interface_matches_prefix(interface_name, config->hotspot_interface_prefixes[index]);
    }
    if (!relevant) return;
    asteriskd_event_batch_record_link(
        events,
        message->nlmsg_type == RTM_DELLINK ? ASTERISKD_EVENT_REMOVED : ASTERISKD_EVENT_UPDATED,
        interface_name);
}

static void record_address_message(
    const struct asteriskd_config *config,
    const struct nlmsghdr *message,
    struct asteriskd_event_batch *events) {
    if (message->nlmsg_len < NLMSG_LENGTH(sizeof(struct ifaddrmsg))) return;
    const struct ifaddrmsg *address_info = (const struct ifaddrmsg *)NLMSG_DATA(message);
    if (address_info->ifa_family != AF_INET && address_info->ifa_family != AF_INET6) return;
    char interface_name[ASTERISKD_MAX_INTERFACE_NAME];
    if (if_indextoname(address_info->ifa_index, interface_name) == NULL ||
        !asteriskd_should_track_interface(config, interface_name)) {
        return;
    }
    const void *address = NULL;
    int remaining = IFA_PAYLOAD(message);
    for (const struct rtattr *attribute = IFA_RTA(address_info);
         RTA_OK(attribute, remaining);
         attribute = RTA_NEXT(attribute, remaining)) {
        if (attribute->rta_type == IFA_LOCAL) {
            address = RTA_DATA(attribute);
            if (address_info->ifa_family == AF_INET) break;
        } else if (attribute->rta_type == IFA_ADDRESS && address == NULL) {
            address = RTA_DATA(attribute);
        }
    }
    if (address == NULL) return;
    char text[64];
    if (inet_ntop(address_info->ifa_family, address, text, sizeof(text)) == NULL) return;
    asteriskd_event_batch_record_address(
        events,
        message->nlmsg_type == RTM_DELADDR ? ASTERISKD_EVENT_REMOVED : ASTERISKD_EVENT_ADDED,
        address_info->ifa_family,
        interface_name,
        text);
}

static void record_netlink_message(
    const struct asteriskd_config *config,
    const struct nlmsghdr *message,
    struct asteriskd_event_batch *events) {
    switch (message->nlmsg_type) {
        case RTM_NEWLINK:
        case RTM_DELLINK:
            record_link_message(config, message, events);
            return;
        case RTM_NEWADDR:
        case RTM_DELADDR:
            record_address_message(config, message, events);
            return;
        default:
            return;
    }
}

static int drain_netlink_socket(
    int fd,
    const struct asteriskd_config *config,
    struct asteriskd_event_batch *events) {
    char buffer[8192];
    while (true) {
        ssize_t count = recv(fd, buffer, sizeof(buffer), MSG_DONTWAIT);
        if (count >= 0) {
            if (count == 0) return 0;
            unsigned int remaining = (unsigned int)count;
            for (const struct nlmsghdr *message = (const struct nlmsghdr *)buffer;
                 NLMSG_OK(message, remaining);
                 message = NLMSG_NEXT(message, remaining)) {
                record_netlink_message(config, message, events);
            }
            continue;
        }
        if (errno == EAGAIN || errno == EWOULDBLOCK) return 0;
        if (errno == EINTR) continue;
        return -1;
    }
}

static int write_pid_file(const struct asteriskd_state *state) {
    FILE *file = fopen(state->pid_path, "w");
    if (file == NULL) return -1;
    int result = fprintf(file, "%ld\n", (long)getpid()) < 0 || fclose(file) != 0 ? -1 : 0;
    return result;
}

static int write_ready_file(const struct asteriskd_state *state) {
    FILE *file = fopen(state->ready_path, "w");
    if (file == NULL) return -1;
    int result = fputs("ready\n", file) == EOF || fclose(file) != 0 ? -1 : 0;
    return result;
}

int asteriskd_prepare(const struct asteriskd_config *config, struct asteriskd_state *state) {
    (void)state;
    if (restore_stale_ipv6_state(config) != 0) return -1;
    return asteriskd_prepare_iptables_bypass(config);
}

int asteriskd_run(const struct asteriskd_config *config, struct asteriskd_state *state) {
    asteriskd_open_log(state);
    asteriskd_log(state, "starting asteriskd");
    struct sigaction action;
    memset(&action, 0, sizeof(action));
    action.sa_handler = on_signal;
    sigemptyset(&action.sa_mask);
    (void)sigaction(SIGTERM, &action, NULL);
    (void)sigaction(SIGINT, &action, NULL);
    if (write_pid_file(state) != 0) asteriskd_fail_stop(config, state, "write pid");
    int netlink_fd = open_netlink_socket(config);
    if (netlink_fd < 0) asteriskd_fail_stop(config, state, "open netlink");
    bool addresses_changed = false;
    if (asteriskd_sync_all(config, state, true, true, &addresses_changed) != 0) {
        asteriskd_fail_stop(config, state, "initial sync");
    }
    if (write_ready_file(state) != 0) asteriskd_fail_stop(config, state, "write ready");

    struct asteriskd_event_batch events;
    asteriskd_event_batch_init(&events);
    long long sync_deadline = -1LL;
    while (!stop_requested) {
        int timeout = -1;
        if (sync_deadline >= 0LL) {
            long long now = monotonic_millis();
            if (now < 0LL) asteriskd_fail_stop(config, state, "read monotonic clock");
            long long remaining = sync_deadline - now;
            timeout = remaining > 0LL ? (int)remaining : 0;
        }
        struct pollfd poll_fd = {.fd = netlink_fd, .events = POLLIN, .revents = 0};
        int result = poll(&poll_fd, 1U, timeout);
        if (stop_requested) break;
        if (result > 0 && (poll_fd.revents & POLLIN) != 0) {
            size_t event_count_before = events.count;
            bool truncated_before = events.truncated;
            if (drain_netlink_socket(netlink_fd, config, &events) != 0) asteriskd_fail_stop(config, state, "drain netlink");
            if (events.count == event_count_before && events.truncated == truncated_before) continue;
            long long now = monotonic_millis();
            if (now < 0LL) asteriskd_fail_stop(config, state, "read monotonic clock");
            sync_deadline = now + ASTERISKD_SYNC_DEBOUNCE_MILLIS;
        } else if (result == 0 && sync_deadline >= 0LL) {
            bool synchronize_ipv6_interfaces =
                config->disable_system_ipv6 && asteriskd_event_batch_has_link_event(&events);
            bool synchronize_hotspot_interfaces =
                config->enable_ipv6 && asteriskd_event_batch_has_hotspot_interface_event(&events, config);
            bool addresses_changed = false;
            if (asteriskd_sync_all(
                    config,
                    state,
                    synchronize_ipv6_interfaces,
                    synchronize_hotspot_interfaces,
                    &addresses_changed) != 0) {
                asteriskd_fail_stop(config, state, "synchronize addresses");
            }
            if (addresses_changed || synchronize_hotspot_interfaces) {
                asteriskd_log_event_batch(state, &events);
            }
            asteriskd_event_batch_init(&events);
            sync_deadline = -1LL;
        } else if (result < 0 && errno != EINTR) {
            asteriskd_fail_stop(config, state, "poll netlink");
        }
    }
    (void)close(netlink_fd);
    asteriskd_restore_ipv6(config, state);
    (void)unlink(state->ready_path);
    (void)unlink(state->pid_path);
    asteriskd_log(state, "stopped asteriskd");
    asteriskd_close_log(state);
    return 0;
}


