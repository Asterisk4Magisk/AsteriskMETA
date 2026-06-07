cmake_minimum_required(VERSION 3.0)

# 获取 mihomo submodule 版本信息
set(MIHOMO_SOURCE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../foss/golang/clash")
execute_process(
  COMMAND git -C ${MIHOMO_SOURCE_DIR} rev-parse --short=7 HEAD
  OUTPUT_VARIABLE COMMIT_HASH
  OUTPUT_STRIP_TRAILING_WHITESPACE
)
if (NOT COMMIT_HASH)
  set(COMMIT_HASH "unknown")
endif ()
message(STATUS "mihomo git hash = ${COMMIT_HASH}")

set(CURRENT_BRANCH "${MIHOMO_CORE_VERSION}")
if (NOT CURRENT_BRANCH)
  execute_process(
    COMMAND git -C ${MIHOMO_SOURCE_DIR} describe --tags --exact-match HEAD
    OUTPUT_VARIABLE CURRENT_BRANCH
    OUTPUT_STRIP_TRAILING_WHITESPACE
    ERROR_QUIET
  )
endif ()
if (NOT CURRENT_BRANCH)
  set(CURRENT_BRANCH "unknown")
endif ()
message(STATUS "mihomo version = ${CURRENT_BRANCH}")

# 获取生成时间
string(TIMESTAMP COMPILE_TIME "%y%m%d")
string (REGEX REPLACE "[\n\t\r]" "" COMPILE_TIME ${COMPILE_TIME})
string(REGEX REPLACE "\"" "" COMPILE_TIME ${COMPILE_TIME})

# 生成版本信息
set(GIT_VERSION "${CURRENT_BRANCH}_${COMMIT_HASH}_${COMPILE_TIME}")
message(STATUS "version info = ${GIT_VERSION}")

# 去除空格
string(REGEX REPLACE "[ ]+" "" GIT_VERSION "${GIT_VERSION}")

# 保存变量到构建目录，避免多 ABI 并发配置时写回源码目录
configure_file("${CMAKE_CURRENT_SOURCE_DIR}/version.h.in" "${CMAKE_CURRENT_BINARY_DIR}/version.h" @ONLY)

project(clash-bridge C)


set(CMAKE_POSITION_INDEPENDENT_CODE on)
set(CMAKE_C_FLAGS_RELEASE "${CMAKE_C_FLAGS_RELEASE} -O3")

set(GO_OUTPUT_BASE ${GO_OUTPUT}/${FLAVOR_NAME})

if ("${CMAKE_BUILD_TYPE}" STREQUAL "Debug")
    set(GO_OUTPUT_BASE "${GO_OUTPUT_BASE}Debug")
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "Release")
    set(GO_OUTPUT_BASE "${GO_OUTPUT_BASE}Release")
elseif ("${CMAKE_BUILD_TYPE}" STREQUAL "RelWithDebInfo")
    set(GO_OUTPUT_BASE "${GO_OUTPUT_BASE}Release")
else ()
    message(FATAL_ERROR "Unknown build type ${CMAKE_BUILD_TYPE}")
endif ()

include_directories("${GO_OUTPUT_BASE}/${CMAKE_ANDROID_ARCH_ABI}")
include_directories("${GO_SOURCE}")
include_directories("${CMAKE_CURRENT_BINARY_DIR}")

link_directories("${GO_OUTPUT_BASE}/${CMAKE_ANDROID_ARCH_ABI}")

add_library(bridge SHARED main.c jni_helper.c bridge_helper.c)
target_link_libraries(bridge log clash)
