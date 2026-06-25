cmake_minimum_required(VERSION 3.20)
project(axs-native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ─── 平台架构强制 ────────────────────────────────────────────────
if(WIN32)
    if(NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
        message(FATAL_ERROR "axs-native 仅支持 64 位架构")
    endif()
endif()

# ─── JNI ────────────────────────────────────────────────────────
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

# ─── OpenSSL（AES-GCM / ChaCha20 / SHA-256 / HMAC / Ed25519） ──
if(MINGW)
    set(OPENSSL_USE_STATIC_LIBS TRUE)
endif()
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
    # 原有模块
    src/axs_native.cpp
    src/resource_decrypt.cpp
    src/key_unwrap.cpp
    src/env_check.cpp
    src/module_decrypt.cpp
    # JAR 保护层新增
    src/class_decrypt.cpp
    src/anti_debug_enhanced.cpp
    src/integrity_check.cpp
    src/hw_fingerprint.cpp
)

add_library(axs-native SHARED ${SOURCES})
target_include_directories(axs-native PRIVATE include ${JNI_INCLUDE_DIRS})
target_link_libraries(axs-native PRIVATE OpenSSL::Crypto zlibstatic)

# Windows: 需要 psapi 和 ws2_32（完整性校验 + Frida 端口检测）
if(WIN32)
    target_link_libraries(axs-native PRIVATE psapi ws2_32 iphlpapi)
endif()

# MinGW 静态链接运行时库
if(MINGW)
    target_compile_options(axs-native PRIVATE -static-libgcc -static-libstdc++)
    target_link_options(axs-native PRIVATE -static-libgcc -static-libstdc++)
    execute_process(
        COMMAND x86_64-w64-mingw32-gcc -print-file-name=libgcc.a
        OUTPUT_VARIABLE MINGW_LIBGCC OUTPUT_STRIP_TRAILING_WHITESPACE
    )
    execute_process(
        COMMAND x86_64-w64-mingw32-g++ -print-file-name=libstdc++.a
        OUTPUT_VARIABLE MINGW_LIBSTDCPP OUTPUT_STRIP_TRAILING_WHITESPACE
    )
    execute_process(
        COMMAND x86_64-w64-mingw32-gcc -print-file-name=libwinpthread.a
        OUTPUT_VARIABLE MINGW_LIBWINPTHREAD OUTPUT_STRIP_TRAILING_WHITESPACE
    )
    target_link_libraries(axs-native PRIVATE
        "${MINGW_LIBGCC}" "${MINGW_LIBSTDCPP}" "${MINGW_LIBWINPTHREAD}"
    )
endif()

# ─── OLLVM 混淆（可选，极高保护级别） ───────────────────────────
option(ENABLE_OLLVM "Enable OLLVM obfuscation" OFF)
if(ENABLE_OLLVM)
    target_compile_options(axs-native PRIVATE
        -mllvm -fla    # 控制流平坦化
        -mllvm -bcf    # 虚假控制流
        -mllvm -sub    # 指令替换
    )
endif()

# ─── Release 优化 ──────────────────────────────────────────────
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    if(UNIX OR MINGW)
        target_link_options(axs-native PRIVATE -s) # strip symbols
    endif()
    target_compile_options(axs-native PRIVATE -O2)
    if(UNIX AND NOT APPLE)
        target_compile_options(axs-native PRIVATE
            -fvisibility=hidden -fstack-protector-strong
        )
        target_link_options(axs-native PRIVATE -Wl,--exclude-libs,ALL)
    endif()
endif()

# ─── 输出路径 ───────────────────────────────────────────────────
set_target_properties(axs-native PROPERTIES
    LIBRARY_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/../src/main/resources/native"
    RUNTIME_OUTPUT_DIRECTORY "${CMAKE_SOURCE_DIR}/../src/main/resources/native"
)
