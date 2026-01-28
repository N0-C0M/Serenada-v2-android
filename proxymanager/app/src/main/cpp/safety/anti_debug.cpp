//
// Created by n on 16.08.2025.
//

#include <jni.h>
#include <unistd.h>
#include <sys/ptrace.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <string.h>
#include <android/log.h>
#include <dlfcn.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <linux/unistd.h>
#include <sys/system_properties.h>
#include <time.h>
#include <stdlib.h>
#include <arpa/inet.h>
#define AsmBreakingStub asm ( "adr x1, #0x10\n" "ldr x0, [x1, #61]\n" "ldr x1, #16\n" "blr x1\n" "ldr x1, #48\n" "blr x3\n" ".byte 0xF1, 0xFF, 0xF2, 0xA2\n" ".byte 0xF8, 0xFF, 0xE2, 0xC2\n" );
#define LOG_TAG "AntiDebug"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)


int check_tracerpid() {
    int status_fd = open("/proc/self/status", O_RDONLY);
    if (status_fd == -1) return 0;

    char buffer[1024];
    ssize_t bytes_read = read(status_fd, buffer, sizeof(buffer) - 1);
    close(status_fd);

    if (bytes_read <= 0) return 0;

    buffer[bytes_read] = '\0';
    const char* tracer_pid_str = strstr(buffer, "TracerPid:");
    if (!tracer_pid_str) return 0;

    long tracer_pid = strtol(tracer_pid_str + 10, NULL, 10);
    return tracer_pid != 0;
}

int check_ptrace() {
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) == -1) {
        return 1; // Обнаружен отладчик
    }
    return 0;
}

int check_timing() {
    struct timespec start, end;
    clock_gettime(CLOCK_MONOTONIC, &start);

    volatile int i;
    for (i = 0; i < 1000000; i++);

    clock_gettime(CLOCK_MONOTONIC, &end);
    long elapsed_ns = (end.tv_sec - start.tv_sec) * 1000000000 + (end.tv_nsec - start.tv_nsec);

    return elapsed_ns > 50000000; // Порог 50 мс
}

int check_debug_tools() {

    void* handle = dlopen("libfrida-gadget.so", RTLD_NOW | RTLD_NOLOAD);
    if (handle) {
        dlclose(handle);
        return 1;
    }


    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock != -1) {
        struct sockaddr_in addr;
        memset(&addr, 0, sizeof(addr));
        addr.sin_family = AF_INET;
        addr.sin_port = htons(27042);


        close(sock);
    }
    return 0;
}


int check_debug_files() {
    const char* debug_files[] = {
            "/frida",
            "/xposed",
            "/sbin/magisk",
            "/system/lib/libfrida-gadget.so",
            "/system/bin/su"
    };

    for (int i = 0; i < sizeof(debug_files)/sizeof(debug_files[0]); i++) {
        if (access(debug_files[i], F_OK) == 0) {
            return 1;
        }
    }
    return 0;
}


void detect_debugger() {
    AsmBreakingStub;
    int detected = 0;
    detected |= check_tracerpid();
    detected |= check_ptrace();
    detected |= check_timing();
    detected |= check_debug_tools();
    //detected |= check_emulator();
    detected |= check_debug_files();

    if (detected) {
        kill(getpid(), SIGKILL);
    }
}
