#include "axs_native.h"

#ifdef _WIN32
#include <windows.h>
#include <tlhelp32.h>
#else
#include <sys/ptrace.h>
#include <sys/stat.h>
#include <fstream>
#include <string>
#include <unistd.h>
#endif

static constexpr int FLAG_AGENT    = 1;  // bit 0
static constexpr int FLAG_DEBUGGER = 2;  // bit 1
static constexpr int FLAG_TAMPER   = 4;  // bit 2

#ifdef _WIN32

static bool check_debugger_win() {
    if (IsDebuggerPresent()) return true;

    BOOL remote = FALSE;
    CheckRemoteDebuggerPresent(GetCurrentProcess(), &remote);
    return remote != FALSE;
}

static bool check_suspicious_processes() {
    // 检测常见的 Java Agent dump / 逆向工具进程
    const wchar_t *suspicious[] = {
        L"jad.exe", L"jadx.exe", L"cfr.exe", L"procyon.exe",
        L"recaf.exe", L"bytecodeviewer.exe", L"arthas.exe",
        nullptr
    };

    HANDLE snap = CreateToolhelp32Snapshot(TH32CS_SNAPPROCESS, 0);
    if (snap == INVALID_HANDLE_VALUE) return false;

    PROCESSENTRY32W entry{};
    entry.dwSize = sizeof(entry);
    bool found = false;

    if (Process32FirstW(snap, &entry)) {
        do {
            for (int i = 0; suspicious[i]; i++) {
                if (_wcsicmp(entry.szExeFile, suspicious[i]) == 0) {
                    found = true;
                    break;
                }
            }
        } while (!found && Process32NextW(snap, &entry));
    }
    CloseHandle(snap);
    return found;
}

#else // Linux

static bool check_debugger_linux() {
    // /proc/self/status 中 TracerPid != 0 表示被调试
    std::ifstream status("/proc/self/status");
    std::string line;
    while (std::getline(status, line)) {
        if (line.rfind("TracerPid:", 0) == 0) {
            int pid = std::stoi(line.substr(10));
            return pid != 0;
        }
    }
    return false;
}

static bool check_ptrace() {
    // 如果 ptrace(TRACEME) 失败，说明已被 attach
    return ptrace(PTRACE_TRACEME, 0, nullptr, nullptr) < 0;
}

#endif

jint environmentCheck(JNIEnv *env, jclass clazz) {
    int flags = 0;

#ifdef _WIN32
    if (check_debugger_win())        flags |= FLAG_DEBUGGER;
    if (check_suspicious_processes()) flags |= FLAG_AGENT;
#else
    if (check_debugger_linux())      flags |= FLAG_DEBUGGER;
    // ptrace 检测仅在非调试模式下执行，避免误报
    if (!(flags & FLAG_DEBUGGER) && check_ptrace()) flags |= FLAG_DEBUGGER;
#endif

    // 双向校验：回调 Java 层验证入口 t0()，确认 Java 层未被剥离或篡改
    jmethodID verifyMethod = env->GetStaticMethodID(clazz, "t0", "()Z");
    if (!verifyMethod) {
        flags |= FLAG_TAMPER;
    } else {
        jboolean ok = env->CallStaticBooleanMethod(clazz, verifyMethod);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            flags |= FLAG_TAMPER;
        } else if (!ok) {
            flags |= FLAG_TAMPER;
        }
    }

    return flags;
}

bool native_hard_reject_signal(JNIEnv *env) {
    int flags = 0;

#ifdef _WIN32
    if (check_debugger_win())        flags |= FLAG_DEBUGGER;
    if (check_suspicious_processes()) flags |= FLAG_AGENT;
#else
    if (check_debugger_linux())      flags |= FLAG_DEBUGGER;
#endif

    bool java_reject = false;
    if (env) {
        jclass antiDebug = env->FindClass("xuanmo/arcartxsuite/security/protection/JvmAntiDebug");
        if (antiDebug) {
            jmethodID hardReject = env->GetStaticMethodID(antiDebug, "hasHardRejectSignal", "()Z");
            if (hardReject) {
                jboolean ok = env->CallStaticBooleanMethod(antiDebug, hardReject);
                if (!env->ExceptionCheck()) {
                    java_reject = (ok == JNI_TRUE);
                } else {
                    env->ExceptionClear();
                }
            } else if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
        } else if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
    }

    return java_reject || flags != 0;
}
