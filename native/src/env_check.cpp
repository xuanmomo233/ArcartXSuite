#include "axs_native.h"

#ifdef _WIN32
#include <windows.h>
#include <tlhelp32.h>
#else
#include <sys/stat.h>
#include <fstream>
#include <string>
#include <unistd.h>
#endif

// 威胁标志位。注意与 Java 侧 ProtectionEnvironment 的 NATIVE_HARD_MASK(0x07) 对齐：
// 仅 bit0-2 会触发硬降级。bit3+ 为软信号（仅上报/日志，不影响运行）——同机存在
// 反编译工具不代表本进程被注入，进程名匹配硬拒会误杀合法运维。
static constexpr int FLAG_AGENT    = 1;  // bit 0
static constexpr int FLAG_DEBUGGER = 2;  // bit 1
static constexpr int FLAG_TAMPER   = 4;  // bit 2
static constexpr int FLAG_PROCESS  = 8;  // bit 3（软信号：同机可疑进程，不进硬掩码）

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

// 注意：这里刻意不做 ptrace(PTRACE_TRACEME) 主动探测——TRACEME 成功后进程
// TracerPid 永久变为父进程 PID，下一次 TracerPid 检查会把自己误判为被调试；
// 同时它会让进程此后无法被 async-profiler 等合法工具 attach。TracerPid 读取
// 已覆盖“正被跟踪”这一威胁。

#endif

jint environmentCheck(JNIEnv *env, jclass clazz) {
    int flags = 0;

#ifdef _WIN32
    if (check_debugger_win())        flags |= FLAG_DEBUGGER;
    if (check_suspicious_processes()) flags |= FLAG_PROCESS; // 软信号
#else
    if (check_debugger_linux())      flags |= FLAG_DEBUGGER;
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

    // 硬拒绝只采信高置信度信号（本进程正被调试）。可疑进程名是同机证据而非
    // 注入证据，不参与硬拒绝——避免腐竹在同机开反编译工具导致插件被误杀。
#ifdef _WIN32
    if (check_debugger_win())        flags |= FLAG_DEBUGGER;
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
