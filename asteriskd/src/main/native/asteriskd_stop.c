// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "asteriskd.h"

#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

extern char **environ;

static pid_t read_pid_file(const char *path) {
    FILE *file = fopen(path, "r");
    if (file == NULL) return -1;
    long value = -1L;
    int parsed = fscanf(file, "%ld", &value);
    (void)fclose(file);
    if (parsed != 1 || value <= 1L || value > INT32_MAX) return -1;
    return (pid_t)value;
}

static bool process_matches(pid_t pid, const char *marker) {
    char path[64];
    if (snprintf(path, sizeof(path), "/proc/%ld/cmdline", (long)pid) <= 0) return false;
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    char command_line[4096];
    ssize_t count = read(fd, command_line, sizeof(command_line) - 1U);
    (void)close(fd);
    if (count <= 0) return false;
    for (ssize_t index = 0; index < count; ++index) {
        if (command_line[index] == '\0') command_line[index] = ' ';
    }
    command_line[count] = '\0';
    return strstr(command_line, marker) != NULL;
}

static void terminate_validated_process(const char *pid_path, const char *marker) {
    pid_t pid = read_pid_file(pid_path);
    if (pid <= 1 || !process_matches(pid, marker)) return;
    (void)kill(pid, SIGTERM);
    usleep(200000U);
    if (kill(pid, 0) == 0) (void)kill(pid, SIGKILL);
}

static void emergency_stop_proxy_processes(const struct asteriskd_config *config) {
    char path[ASTERISKD_MAX_PATH];
    if (snprintf(path, sizeof(path), "%s/mihomo-root.pid", config->data_directory) > 0) {
        terminate_validated_process(path, "config-root.yaml");
    }
    if (snprintf(path, sizeof(path), "%s/bpf2socks.pid", config->data_directory) > 0) {
        terminate_validated_process(path, "bpf2socks.json");
    }
    if (snprintf(path, sizeof(path), "%s/tun2socks.pid", config->data_directory) > 0) {
        terminate_validated_process(path, "tun2socks.yml");
    }
}

_Noreturn void asteriskd_fail_stop(
    const struct asteriskd_config *config,
    struct asteriskd_state *state,
    const char *step) {
    int saved_errno = errno;
    asteriskd_log(state, "fail-stop at %s: %s", step, strerror(saved_errno));
    asteriskd_restore_ipv6(config, state);
    char *arguments[] = {"sh", (char *)config->stop_script_path, "--from-asteriskd", NULL};
    execve("/system/bin/sh", arguments, environ);
    asteriskd_log(state, "fail-stop exec failed: %s", strerror(errno));
    emergency_stop_proxy_processes(config);
    _exit(1);
}



