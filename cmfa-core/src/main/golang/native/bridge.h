#pragma once

#include <stddef.h>
#include <stdint.h>
#include <malloc.h>
#include <android/log.h>

#define TAG "ClashMetaForAndroid"

typedef const char *c_string;
typedef uintptr_t c_object;

extern void (*mark_socket_func)(void *tun_interface, int fd);

extern int (*query_socket_uid_func)(void *tun_interface, int protocol, const char *source, const char *target);

extern void (*complete_func)(void *completable, const char *exception);

extern void (*fetch_report_func)(void *fetch_callback, const char *status_json);

extern void (*fetch_complete_func)(void *fetch_callback, const char *error);

extern int (*logcat_received_func)(void *logcat_interface, const char *payload);

extern void (*release_object_func)(void *obj);

extern int (*open_content_func)(const char *url, char *error, int error_length);

// cgo
extern void mark_socket(c_object interface, int fd);

extern int query_socket_uid(c_object interface, int protocol, char *source, char *target);

extern void complete(c_object obj, char *error);

extern void fetch_complete(c_object completable, char *exception);

extern void fetch_report(c_object fetch_callback, char *status_json);

extern int logcat_received(c_object logcat_interface, char *payload);

extern void release_object(c_object obj);

extern int open_content(char *url, char *error, int error_length);

extern void log_info(char *msg);

extern void log_error(char *msg);

extern void log_warn(char *msg);

extern void log_debug(char *msg);

extern void log_verbose(char *msg);
