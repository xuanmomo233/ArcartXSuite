#include "axs_native.h"
#include <cstring>
#include <ctime>

#ifdef _WIN32
#include <windows.h>
#include <winternl.h>
#include <tlhelp32.h>
#include <iphlpapi.h>
#include <winsock2.h>
#pragma comment(lib, "iphlpapi.lib")
#pragma comment(lib, "ws2_32.lib")
#else
#include <sys/ptrace.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>
#include <fstream>
#include <string>
#include <dirent.h>
#include <dlfcn.h>
#include <link.h>
#endif

// 威胁标志位（与 Java 层对齐）
static constexpr int THREAT_DEBUGGER    = 0x0001;
static constexpr int THREAT_FRIDA       = 0x0002;
static constexpr int THREAT_HW_BP       = 0x0004;
static constexpr int THREAT_HOOK        = 0x0008;
static constexpr int THREAT_TIMING      = 0x0010;
static constexpr int THREAT_PROCESS     = 0x0020;

// ─── 时间差检测（通用） ──────────────────────────────────────────

static bool check_timing_anomaly() {
#ifdef _WIN32
    LARGE_INTEGER freq, start, end;
    QueryPerformanceFrequency(&freq);
    QueryPerformanceCounter(&start);
    // 执行一段密集计算
    volatile int x = 0;
    for (int i = 0; i < 100000; i++) x += i;
    QueryPerformanceCounter(&end);
    double ms = (double)(end.QuadPart - start.QuadPart) * 1000.0 / freq.QuadPart;
    return ms > 50.0; // 正常应 < 5ms
#else
    struct timespec start, end;
    clock_gettime(CLOCK_MONOTONIC, &start);
    volatile int x = 0;
    for (int i = 0; i < 100000; i++) x += i;
    clock_gettime(CLOCK_MONOTONIC, &end);
    long ms = (end.tv_sec - start.tv_sec) * 1000 + (end.tv_nsec - start.tv_nsec) / 1000000;
    return ms > 50;
#endif
}

// ─── Frida 检测 ──────────────────────────────────────────────────

static bool check_frida_port() {
    // Frida 默认端口 27042/27043
    int ports[] = {27042, 27043};
    for (int port : ports) {
#ifdef _WIN32
        SOCKET s = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (s == INVALID_SOCKET) continue;
        struct sockaddr_in addr = {};
        addr.sin_family = AF_INET;
        addr.sin_port = htons(port);
        addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        // 非阻塞连接尝试
        u_long mode = 1;
        ioctlsocket(s, FIONBIO, &mode);
        int ret = connect(s, (struct sockaddr*)&addr, sizeof(addr));
        closesocket(s);
        if (ret == 0) return true;
#else
        int s = socket(AF_INET, SOCK_STREAM, 0);
        if (s < 0) continue;
        struct sockaddr_in addr = {};
        addr.sin_family = AF_INET;
        addr.sin_port = htons(port);
        addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        struct timeval tv = {0, 100000}; // 100ms timeout
        setsockopt(s, SOL_SOCKET, SO_SNDTIMEO, &tv, sizeof(tv));
        int ret = connect(s, (struct sockaddr*)&addr, sizeof(addr));
        close(s);
        if (ret == 0) return true;
#endif
    }
    return false;
}

#ifdef _WIN32

// ─── Windows 增强反调试 ─────────────────────────────────────────

static bool check_hardware_breakpoints() {
    CONTEXT ctx = {};
    ctx.ContextFlags = CONTEXT_DEBUG_REGISTERS;
    if (GetThreadContext(GetCurrentThread(), &ctx)) {
        if (ctx.Dr0 || ctx.Dr1 || ctx.Dr2 || ctx.Dr3 || (ctx.Dr7 & 0xFF))
            return true;
    }
    return false;
}

static bool check_peb_debug_flags() {
    // 直接读 PEB 绕过 IsDebuggerPresent hook
#ifdef _WIN64
    unsigned char *peb = (unsigned char*)__readgsqword(0x60);
#else
    unsigned char *peb = (unsigned char*)__readfsdword(0x30);
#endif
    // PEB.BeingDebugged (offset 2)
    if (peb[2]) return true;
    // NtGlobalFlag (offset 0xBC for x64, 0x68 for x86)
#ifdef _WIN64
    ULONG ntFlag = *(ULONG*)(peb + 0xBC);
#else
    ULONG ntFlag = *(ULONG*)(peb + 0x68);
#endif
    if (ntFlag & 0x70) return true; // FLG_HEAP_ENABLE_*
    return false;
}

static bool check_frida_modules() {
    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPMODULE, GetCurrentProcessId());
    if (snap == INVALID_HANDLE_VALUE) return false;
    MODULEENTRY32W me = {};
    me.dwSize = sizeof(me);
    bool found = false;
    if (Module32FirstW(snap, &me)) {
        do {
            const wchar_t *name = me.szModule;
            if (wcsstr(name, L"frida") || wcsstr(name, L"gadget")
                || wcsstr(name, L"substrat")) {
                found = true;
                break;
            }
        } while (Module32NextW(snap, &me));
    }
    CloseHandle(snap);
    return found;
}

#else // Linux

static bool check_frida_maps() {
    std::ifstream maps("/proc/self/maps");
    std::string line;
    while (std::getline(maps, line)) {
        if (line.find("frida") != std::string::npos
            || line.find("gadget") != std::string::npos
            || line.find("substrate") != std::string::npos) {
            return true;
        }
    }
    return false;
}

static bool check_frida_threads() {
    DIR *dir = opendir("/proc/self/task");
    if (!dir) return false;
    struct dirent *entry;
    bool found = false;
    while ((entry = readdir(dir)) != nullptr) {
        if (entry->d_name[0] == '.') continue;
        char path[256];
        snprintf(path, sizeof(path), "/proc/self/task/%s/comm", entry->d_name);
        std::ifstream comm(path);
        std::string name;
        if (std::getline(comm, name)) {
            if (name.find("frida") != std::string::npos
                || name.find("gum-js") != std::string::npos
                || name.find("gmain") != std::string::npos) {
                found = true;
                break;
            }
        }
    }
    closedir(dir);
    return found;
}

#endif

// ─── 增强环境检测（JNI 导出） ────────────────────────────────────

jint enhancedEnvironmentCheck(JNIEnv *env, jclass clazz) {
    int threats = 0;

#ifdef _WIN32
    if (IsDebuggerPresent() || check_peb_debug_flags())
        threats |= THREAT_DEBUGGER;
    if (check_hardware_breakpoints())
        threats |= THREAT_HW_BP;
    if (check_frida_modules())
        threats |= THREAT_FRIDA;
#else
    // TracerPid 检查
    std::ifstream status("/proc/self/status");
    std::string line;
    while (std::getline(status, line)) {
        if (line.rfind("TracerPid:", 0) == 0) {
            if (std::stoi(line.substr(10)) != 0)
                threats |= THREAT_DEBUGGER;
            break;
        }
    }
    if (check_frida_maps() || check_frida_threads())
        threats |= THREAT_FRIDA;
#endif

    if (check_frida_port())
        threats |= THREAT_FRIDA;
    if (check_timing_anomaly())
        threats |= THREAT_TIMING;

    return threats;
}
