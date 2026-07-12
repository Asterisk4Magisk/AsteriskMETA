// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "asteriskd.h"

#include <errno.h>
#include <stdarg.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

static bool is_absolute_path(const char *path) {
    return path != NULL && path[0] == '/';
}

static const char *option_value(int argc, char **argv, const char *option) {
    const char *value = NULL;
    for (int index = 2; index < argc; index += 2) {
        if (index + 1 >= argc) return NULL;
        if (strcmp(argv[index], option) != 0) continue;
        if (value != NULL) return NULL;
        value = argv[index + 1];
    }
    return value;
}

static bool has_only_options(int argc, char **argv, bool start) {
    int index = 2;
    while (index < argc) {
        bool known = strcmp(argv[index], "--config") == 0 ||
            (start && (strcmp(argv[index], "--pid") == 0 || strcmp(argv[index], "--log") == 0));
        if (!known || index + 1 >= argc) return false;
        index += 2;
    }
    return true;
}

void asteriskd_state_init(
    struct asteriskd_state *state,
    const char *pid_path,
    const char *log_path,
    const char *data_directory) {
    memset(state, 0, sizeof(*state));
    state->active_ipv4_slot = 0;
    state->active_ipv6_slot = 0;
    if (pid_path != NULL) (void)snprintf(state->pid_path, sizeof(state->pid_path), "%s", pid_path);
    if (log_path != NULL) (void)snprintf(state->log_path, sizeof(state->log_path), "%s", log_path);
    if (data_directory != NULL) {
        (void)snprintf(state->ready_path, sizeof(state->ready_path), "%s/asteriskd.ready", data_directory);
    }
}

void asteriskd_open_log(struct asteriskd_state *state) {
    if (state->log_path[0] == '\0') {
        state->log_file = stderr;
        return;
    }
    state->log_file = fopen(state->log_path, "a");
    if (state->log_file == NULL) state->log_file = stderr;
    (void)setvbuf(state->log_file, NULL, _IOLBF, 0);
}

void asteriskd_close_log(struct asteriskd_state *state) {
    if (state->log_file != NULL && state->log_file != stderr) fclose(state->log_file);
    state->log_file = NULL;
}

void asteriskd_log(struct asteriskd_state *state, const char *format, ...) {
    FILE *file = state != NULL && state->log_file != NULL ? state->log_file : stderr;
    time_t now = time(NULL);
    struct tm timestamp;
    char text[32] = "time-unavailable";
    if (localtime_r(&now, &timestamp) != NULL) {
        (void)strftime(text, sizeof(text), "%Y-%m-%d %H:%M:%S", &timestamp);
    }
    (void)fprintf(file, "[%s] ", text);
    va_list args;
    va_start(args, format);
    (void)vfprintf(file, format, args);
    va_end(args);
    (void)fputc('\n', file);
}

static int load_config_or_report(const char *path, struct asteriskd_config *config) {
    char message[256];
    if (asteriskd_load_config(path, config, message, sizeof(message)) == 0) return 0;
    fprintf(stderr, "asteriskd config error: %s\n", message);
    return 1;
}

int main(int argc, char **argv) {
    if (argc < 4) {
        fputs("Usage: asteriskd --prepare --config FILE | --start --config FILE --pid FILE --log FILE\n", stderr);
        return 64;
    }
    bool start = strcmp(argv[1], "--start") == 0;
    bool prepare = strcmp(argv[1], "--prepare") == 0;
    const char *config_path = option_value(argc, argv, "--config");
    if ((!start && !prepare) || !has_only_options(argc, argv, start) || !is_absolute_path(config_path)) {
        fputs("invalid asteriskd arguments\n", stderr);
        return 64;
    }
    struct asteriskd_config config;
    if (load_config_or_report(config_path, &config) != 0) return 1;
    if (prepare) {
        struct asteriskd_state state;
        asteriskd_state_init(&state, NULL, NULL, config.data_directory);
        if (asteriskd_prepare(&config, &state) != 0) {
            fputs("asteriskd prepare failed\n", stderr);
            return 1;
        }
        return 0;
    }
    const char *pid_path = option_value(argc, argv, "--pid");
    const char *log_path = option_value(argc, argv, "--log");
    if (!is_absolute_path(pid_path) || !is_absolute_path(log_path)) {
        fputs("asteriskd pid and log paths must be absolute\n", stderr);
        return 64;
    }
    struct asteriskd_state state;
    asteriskd_state_init(&state, pid_path, log_path, config.data_directory);
    return asteriskd_run(&config, &state);
}




