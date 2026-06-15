cmake_minimum_required(VERSION 3.20)
project(axs-native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ─── 平台架构强制 ────────────────────────────────────────────────
if(WIN32)
    # Windows 上强制生成 AMD64 架构，防止默认选中 ARM64
    if(NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
        message(FATAL_ERROR "axs-native 仅支持 64 位架构，当前 CMAKE_SIZEOF_VOID_P=${CMAKE_SIZEOF_VOID_P}")
    endif()
endif()

# ─── JNI ────────────────────────────────────────────────────────
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

# ─── OpenSSL（AES-GCM / SHA-256） ─────────────────────────────
find_package(OpenSSL REQUIRED)

# ─── zlib（GZIP 解压） ──────────────────────────────────────────
include(FetchContent)
FetchContent_Declare(
    zlib
    GIT_REPOSITORY https://github.com/madler/zlib.git
    GIT_TAG v1.3.1
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(zlib)

# ─── 源文件 ─────────────────────────────────────────────────────
set(SOURCES
    src/axs_native.cpp
    src/resource_decrypt.cpp
    src/key_unwrap.cpp
    src/env_check.cpp
    src/module_decrypt.cpp
)

add_library(axs-native SHARED ${SOURCES})
target_include_directories(axs-native PRIVATE include ${JNI_INCLUDE_DIRS})
target_link_libraries(axs-native PRIVATE OpenSSL::Crypto zlibstatic)

# Strip symbols in release
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    if(UNIX)
        target_link_options(axs-native PRIVATE -s)
    endif()
endif()

# ─── 输出路径 ───────────────────────────────────────────────────
set_target_properties(axs-native PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/../src/main/resources/native"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/../src/main/resources/native"
)
