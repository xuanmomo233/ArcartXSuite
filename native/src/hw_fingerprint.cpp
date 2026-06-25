#include "axs_native.h"
#include <openssl/sha.h>
#include <cstring>

#ifdef _WIN32
#include <windows.h>
#include <iphlpapi.h>
#include <intrin.h>
#pragma comment(lib, "iphlpapi.lib")

void get_hardware_fingerprint(uint8_t out[32]) {
    SHA256_CTX sha;
    SHA256_Init(&sha);

    // 1. CPU ID
    int cpuInfo[4] = {0};
    __cpuid(cpuInfo, 0);
    SHA256_Update(&sha, cpuInfo, sizeof(cpuInfo));
    __cpuid(cpuInfo, 1);
    SHA256_Update(&sha, cpuInfo, sizeof(cpuInfo));

    // 2. 卷序列号
    DWORD serial = 0;
    GetVolumeInformationA("C:\\", nullptr, 0, &serial, nullptr, nullptr, nullptr, 0);
    SHA256_Update(&sha, &serial, sizeof(serial));

    // 3. MAC 地址
    ULONG bufLen = 0;
    GetAdaptersInfo(nullptr, &bufLen);
    if (bufLen > 0) {
        auto *info = (IP_ADAPTER_INFO*)malloc(bufLen);
        if (GetAdaptersInfo(info, &bufLen) == ERROR_SUCCESS) {
            SHA256_Update(&sha, info->Address, info->AddressLength);
        }
        free(info);
    }

    // 4. 计算机名
    char compName[MAX_COMPUTERNAME_LENGTH + 1] = {};
    DWORD compSize = sizeof(compName);
    GetComputerNameA(compName, &compSize);
    SHA256_Update(&sha, compName, compSize);

    SHA256_Final(out, &sha);
}

#else // Linux

#include <fstream>
#include <string>
#include <unistd.h>
#include <net/if.h>
#include <sys/ioctl.h>

void get_hardware_fingerprint(uint8_t out[32]) {
    SHA256_CTX sha;
    SHA256_Init(&sha);

    // 1. machine-id
    std::ifstream mid("/etc/machine-id");
    std::string machineId;
    if (std::getline(mid, machineId)) {
        SHA256_Update(&sha, machineId.c_str(), machineId.size());
    }

    // 2. CPU info
    std::ifstream cpuinfo("/proc/cpuinfo");
    std::string line;
    while (std::getline(cpuinfo, line)) {
        if (line.find("model name") != std::string::npos
            || line.find("cpu cores") != std::string::npos) {
            SHA256_Update(&sha, line.c_str(), line.size());
        }
    }

    // 3. MAC 地址（第一个非 loopback 接口）
    int sock = socket(AF_INET, SOCK_DGRAM, 0);
    if (sock >= 0) {
        struct ifreq ifr = {};
        strncpy(ifr.ifr_name, "eth0", IFNAMSIZ - 1);
        if (ioctl(sock, SIOCGIFHWADDR, &ifr) == 0) {
            SHA256_Update(&sha, ifr.ifr_hwaddr.sa_data, 6);
        }
        close(sock);
    }

    // 4. hostname
    char hostname[256] = {};
    gethostname(hostname, sizeof(hostname));
    SHA256_Update(&sha, hostname, strlen(hostname));

    SHA256_Final(out, &sha);
}

#endif

// JNI 导出：获取硬件指纹哈希
jbyteArray getHardwareFingerprint(JNIEnv *env, jclass clazz) {
    uint8_t fp[32];
    get_hardware_fingerprint(fp);
    jbyteArray result = env->NewByteArray(32);
    env->SetByteArrayRegion(result, 0, 32, reinterpret_cast<jbyte*>(fp));
    memset(fp, 0, sizeof(fp));
    return result;
}
