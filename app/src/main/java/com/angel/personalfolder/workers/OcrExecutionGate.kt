package com.angel.personalfolder.workers

import kotlinx.coroutines.sync.Mutex

/**
 * Process-local admission gate for heavyweight OCR work.
 *
 * Only the worker that owns this gate may create/render OCR bitmaps and load the
 * Tesseract engine. Waiting jobs therefore stay lightweight and are processed
 * one at a time. The lock is always released even when one document fails.
 */
object OcrExecutionGate {
    private val mutex = Mutex()

    suspend fun <T> runSerial(block: suspend () -> T): T {
        mutex.lock()
        return try {
            block()
        } finally {
            mutex.unlock()
        }
    }

    suspend fun awaitIdle() {
        mutex.lock()
        mutex.unlock()
    }
}
