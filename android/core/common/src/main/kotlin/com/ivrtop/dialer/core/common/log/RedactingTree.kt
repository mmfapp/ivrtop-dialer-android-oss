package com.ivrtop.dialer.core.common.log

import android.util.Log
import timber.log.Timber
import java.util.concurrent.ArrayBlockingQueue

/**
 * Timber tree that redacts every message and keeps an in-memory ring buffer
 * used by Settings › Support › "Export technical report" (user-initiated only).
 */
class RedactingTree(private val logToLogcat: Boolean, capacity: Int = 2000) : Timber.Tree() {

    private val buffer = ArrayBlockingQueue<String>(capacity)

    override fun isLoggable(tag: String?, priority: Int): Boolean = logToLogcat || priority >= Log.INFO

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val clean = Redactor.redact(message)
        val line = "${System.currentTimeMillis()} ${priorityChar(priority)}/${tag ?: "ivrtop"}: $clean" +
            (t?.let { " | ${it.javaClass.simpleName}: ${Redactor.redact(it.message.orEmpty())}" } ?: "")
        synchronized(buffer) {
            if (!buffer.offer(line)) {
                buffer.poll()
                buffer.offer(line)
            }
        }
        if (logToLogcat) Log.println(priority, tag ?: "ivrtop", clean)
    }

    fun snapshot(): List<String> = synchronized(buffer) { buffer.toList() }

    private fun priorityChar(p: Int) = when (p) {
        Log.VERBOSE -> 'V'; Log.DEBUG -> 'D'; Log.INFO -> 'I'; Log.WARN -> 'W'; Log.ERROR -> 'E'; else -> 'A'
    }

    companion object {
        @Volatile var instance: RedactingTree? = null
    }
}
