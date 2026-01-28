//
// Created by n on 16.08.2025.
//

#ifndef REE_V3_ANTI_DEBUG_H
#define REE_V3_ANTI_DEBUG_H

#endif //REE_V3_ANTI_DEBUG_H
#ifndef ANTI_DEBUG_H
#define ANTI_DEBUG_H

#ifdef __cplusplus
#include <sys/ptrace.h>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "AntiDebug"

void check_anti_debug() {
    // Проверка с помощью ptrace
    if (ptrace(PTRACE_TRACEME, 0, 0, 0) == -1) {


        // Ассемблерный код который выполняется только при обнаружении отладки
        asm volatile (
                "mov x0, #0\n"
                "mov x1, #0\n"
                "ldr x2, [x1]\n"    // Попытка чтения по нулевому адресу
                ::: "x0", "x1", "x2"
                );
    } else {

        ptrace(PTRACE_TRACEME, 0, 0, 0);
    }
}

// Макрос для вставки в код
#define CheckForDebugger() check_anti_debug()

#endif

void detect_debugger();

#ifdef __cplusplus

#endif

#endif // ANTI_DEBUG_H