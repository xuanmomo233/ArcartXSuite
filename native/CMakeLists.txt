cmake_minimum_required(VERSION 3.20)
project(axs-native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

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
