// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

#include "asteriskd.h"

#include <errno.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>

static void set_message(char *message, size_t message_size, const char *format, ...) {
    if (message == NULL || message_size == 0U) return;
    va_list args;
    va_start(args, format);
    (void)vsnprintf(message, message_size, format, args);
    va_end(args);
}

static char *read_file(const char *path, char *message, size_t message_size) {
    FILE *file = fopen(path, "rb");
    if (file == NULL) {
        set_message(message, message_size, "cannot read %s: %s", path, strerror(errno));
        return NULL;
    }
    if (fseek(file, 0L, SEEK_END) != 0) {
        set_message(message, message_size, "cannot seek %s", path);
        fclose(file);
        return NULL;
    }
    long length = ftell(file);
    if (length < 0L || length > 1024L * 1024L) {
        set_message(message, message_size, "invalid config size");
        fclose(file);
        return NULL;
    }
    rewind(file);
    char *data = calloc((size_t)length + 1U, 1U);
    if (data == NULL) {
        set_message(message, message_size, "out of memory");
        fclose(file);
        return NULL;
    }
    if (length > 0L && fread(data, 1U, (size_t)length, file) != (size_t)length) {
        set_message(message, message_size, "cannot read config");
        free(data);
        fclose(file);
        return NULL;
    }
    fclose(file);
    return data;
}

static const char *skip_space(const char *value) {
    while (*value == ' ' || *value == '\n' || *value == '\r' || *value == '\t') ++value;
    return value;
}

static const char *json_value(const char *json, const char *key) {
    char needle[96];
    int count = snprintf(needle, sizeof(needle), "\"%s\"", key);
    if (count <= 0 || (size_t)count >= sizeof(needle)) return NULL;
    const char *value = strstr(json, needle);
    if (value == NULL) return NULL;
    value = strchr(value + strlen(needle), ':');
    return value == NULL ? NULL : skip_space(value + 1);
}

static bool json_uint(const char *json, const char *key, uint32_t *out) {
    const char *value = json_value(json, key);
    if (value == NULL || *value < '0' || *value > '9') return false;
    char *end = NULL;
    unsigned long parsed = strtoul(value, &end, 10);
    if (end == value || parsed > UINT32_MAX) return false;
    *out = (uint32_t)parsed;
    return true;
}

static bool json_bool(const char *json, const char *key, bool *out) {
    const char *value = json_value(json, key);
    if (value == NULL) return false;
    if (strncmp(value, "true", 4U) == 0) {
        *out = true;
        return true;
    }
    if (strncmp(value, "false", 5U) == 0) {
        *out = false;
        return true;
    }
    return false;
}

static bool parse_string(const char *value, char *out, size_t out_size, const char **end_out) {
    if (value == NULL || out_size == 0U || *value != '\"') return false;
    ++value;
    const char *end = value;
    while (*end != '\0' && *end != '\"') {
        if (*end == '\\' || (unsigned char)*end < 0x20U) return false;
        ++end;
    }
    if (*end != '\"' || (size_t)(end - value) >= out_size) return false;
    memcpy(out, value, (size_t)(end - value));
    out[end - value] = '\0';
    if (end_out != NULL) *end_out = end + 1;
    return true;
}

static bool json_string(const char *json, const char *key, char *out, size_t out_size) {
    return parse_string(json_value(json, key), out, out_size, NULL);
}

static bool json_string_array(
    const char *json,
    const char *key,
    char values[][ASTERISKD_MAX_INTERFACE_NAME],
    size_t *count) {
    const char *value = json_value(json, key);
    if (value == NULL || *value != '[') return false;
    *count = 0U;
    value = skip_space(value + 1);
    while (*value != ']') {
        if (*count >= ASTERISKD_MAX_INTERFACES ||
            !parse_string(value, values[*count], ASTERISKD_MAX_INTERFACE_NAME, &value)) {
            return false;
        }
        ++*count;
        value = skip_space(value);
        if (*value == ']') break;
        if (*value != ',') return false;
        value = skip_space(value + 1);
    }
    return true;
}

static bool is_absolute_path(const char *path) {
    return path[0] == '/' && strlen(path) < ASTERISKD_MAX_PATH;
}

static bool is_valid_chain_name(const char *chain) {
    size_t length = strlen(chain);
    if (length == 0U || length >= ASTERISKD_MAX_CHAIN_NAME) return false;
    for (size_t index = 0U; index < length; ++index) {
        char value = chain[index];
        if (!((value >= 'A' && value <= 'Z') || (value >= '0' && value <= '9') || value == '_' || value == '-')) {
            return false;
        }
    }
    return true;
}

static bool is_valid_interface_selector(const char *value, bool allow_prefix) {
    size_t length = strlen(value);
    if (length == 0U || length >= ASTERISKD_MAX_INTERFACE_NAME) return false;
    for (size_t index = 0U; index < length; ++index) {
        char ch = value[index];
        if (ch == '+' && allow_prefix && index + 1U == length) continue;
        if (!((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') ||
              (ch >= '0' && ch <= '9') || ch == '_' || ch == '-' || ch == '.')) {
            return false;
        }
    }
    return true;
}

static bool parse_bypass(const char *json, const char *key, struct asteriskd_bypass_target *target) {
    const char *value = json_value(json, key);
    memset(target, 0, sizeof(*target));
    if (value == NULL) return false;
    if (strncmp(value, "null", 4U) == 0) return true;
    if (*value != '{') return false;
    target->enabled =
        json_string(value, "anchorChain", target->anchor_chain, sizeof(target->anchor_chain)) &&
        json_string(value, "slotAChain", target->slot_a_chain, sizeof(target->slot_a_chain)) &&
        json_string(value, "slotBChain", target->slot_b_chain, sizeof(target->slot_b_chain));
    return target->enabled &&
        is_valid_chain_name(target->anchor_chain) &&
        is_valid_chain_name(target->slot_a_chain) &&
        is_valid_chain_name(target->slot_b_chain);
}

static bool parse_bpf_maps(const char *json, struct asteriskd_bpf_local_maps *maps) {
    const char *value = json_value(json, "bpfLocalMaps");
    memset(maps, 0, sizeof(*maps));
    if (value == NULL) return false;
    if (strncmp(value, "null", 4U) == 0) return true;
    if (*value != '{') return false;
    if (!json_string(value, "ipv4Path", maps->ipv4_path, sizeof(maps->ipv4_path))) return false;
    const char *ipv6_value = json_value(value, "ipv6Path");
    if (ipv6_value == NULL) return false;
    if (strncmp(ipv6_value, "null", 4U) != 0 &&
        !parse_string(ipv6_value, maps->ipv6_path, sizeof(maps->ipv6_path), NULL)) {
        return false;
    }
    maps->enabled = true;
    return is_absolute_path(maps->ipv4_path) && (maps->ipv6_path[0] == '\0' || is_absolute_path(maps->ipv6_path));
}

static bool validate_config(const struct asteriskd_config *config) {
    if (config->version != ASTERISKD_CONFIG_VERSION ||
        !is_absolute_path(config->data_directory) ||
        !is_absolute_path(config->stop_script_path) ||
        !is_absolute_path(config->state_path)) {
        return false;
    }
    if (config->mode == ASTERISKD_MODE_BPF2SOCKS) {
        if (!config->bpf_local_maps.enabled) return false;
    } else if (!config->ipv4_bypass.enabled || (config->enable_ipv6 && !config->ipv6_bypass.enabled)) {
        return false;
    }
    for (size_t index = 0U; index < config->ignored_interface_count; ++index) {
        if (!is_valid_interface_selector(config->ignored_interfaces[index], false)) return false;
    }
    for (size_t index = 0U; index < config->virtual_interface_count; ++index) {
        if (!is_valid_interface_selector(config->virtual_interfaces[index], false)) return false;
    }
    for (size_t index = 0U; index < config->hotspot_interface_prefix_count; ++index) {
        if (!is_valid_interface_selector(config->hotspot_interface_prefixes[index], true)) return false;
    }
    return true;
}

int asteriskd_load_config(const char *path, struct asteriskd_config *out, char *message, size_t message_size) {
    if (path == NULL || out == NULL || !is_absolute_path(path)) {
        set_message(message, message_size, "config path must be absolute");
        return -1;
    }
    char *json = read_file(path, message, message_size);
    if (json == NULL) return -1;
    memset(out, 0, sizeof(*out));
    bool ok = json_uint(json, "version", &out->version) &&
        json_bool(json, "enableIpv6", &out->enable_ipv6) &&
        json_bool(json, "disableSystemIpv6", &out->disable_system_ipv6) &&
        json_string(json, "dataDirectory", out->data_directory, sizeof(out->data_directory)) &&
        json_string(json, "stopScriptPath", out->stop_script_path, sizeof(out->stop_script_path)) &&
        json_string(json, "statePath", out->state_path, sizeof(out->state_path)) &&
        json_string_array(json, "ignoredInterfaces", out->ignored_interfaces, &out->ignored_interface_count) &&
        json_string_array(json, "virtualInterfaces", out->virtual_interfaces, &out->virtual_interface_count) &&
        json_string_array(json, "hotspotInterfacePrefixes", out->hotspot_interface_prefixes, &out->hotspot_interface_prefix_count) &&
        parse_bypass(json, "ipv4Bypass", &out->ipv4_bypass) &&
        parse_bypass(json, "ipv6Bypass", &out->ipv6_bypass) &&
        parse_bpf_maps(json, &out->bpf_local_maps);
    char mode[24] = {0};
    ok = json_string(json, "mode", mode, sizeof(mode)) && ok;
    if (strcmp(mode, "tproxy") == 0) {
        out->mode = ASTERISKD_MODE_TPROXY;
    } else if (strcmp(mode, "tun") == 0) {
        out->mode = ASTERISKD_MODE_TUN;
    } else if (strcmp(mode, "tun2socks") == 0) {
        out->mode = ASTERISKD_MODE_TUN2SOCKS;
    } else if (strcmp(mode, "bpf2socks") == 0) {
        out->mode = ASTERISKD_MODE_BPF2SOCKS;
    } else {
        ok = false;
    }
    if (!ok || !validate_config(out)) {
        set_message(message, message_size, "invalid asteriskd config");
        free(json);
        return -1;
    }
    free(json);
    set_message(message, message_size, "ok");
    return 0;
}
