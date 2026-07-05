package com.jiangdg.ausbc.utils

import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

open class SettableFuture<V> : Future<V> {
    private val lock = Object()
    private var completed = false
    private var cancelled = false
    private var value: V? = null
    private var failure: Throwable? = null

    fun set(value: V?): Boolean = complete(value, null, false)

    fun setException(error: Throwable): Boolean = complete(null, error, false)

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean =
        complete(null, CancellationException("Future.cancel() was called."), true)

    override fun isCancelled(): Boolean = synchronized(lock) {
        cancelled
    }

    override fun isDone(): Boolean = synchronized(lock) {
        completed
    }

    override fun get(): V? = synchronized(lock) {
        while (!completed) {
            lock.wait()
        }
        resultLocked()
    }

    override fun get(timeout: Long, unit: TimeUnit): V? = synchronized(lock) {
        var remainingNanos = unit.toNanos(timeout)
        val deadline = System.nanoTime() + remainingNanos
        while (!completed) {
            if (remainingNanos <= 0L) {
                throw TimeoutException("Timeout waiting for task.")
            }
            TimeUnit.NANOSECONDS.timedWait(lock, remainingNanos)
            remainingNanos = deadline - System.nanoTime()
        }
        resultLocked()
    }

    private fun complete(newValue: V?, error: Throwable?, cancel: Boolean): Boolean = synchronized(lock) {
        if (completed) return false
        value = newValue
        failure = error
        cancelled = cancel
        completed = true
        lock.notifyAll()
        true
    }

    private fun resultLocked(): V? {
        if (cancelled) {
            val cancelError = failure as? CancellationException
                ?: CancellationException("Task was cancelled.")
            throw cancelError
        }
        failure?.let { throw ExecutionException(it) }
        return value
    }
}
