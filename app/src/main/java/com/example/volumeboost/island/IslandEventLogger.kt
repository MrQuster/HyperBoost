package com.example.volumeboost.island

import android.text.format.DateFormat
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date

data class IslandLogEntry(
    val timestampMs: Long,
    val formattedTime: String,
    val seqId: Long,
    val action: String,
    val presenterTier: String,
    val boostPercent: Int,
    val jsonSizeBytes: Int,
    val isSuccess: Boolean,
    val detail: String
)

data class SelfTestCheckItem(
    val stepTitle: String,
    val passed: Boolean,
    val explanation: String
)

object IslandEventLogger {

    const val LOG_TAG = "HyperBoost/Island"
    private const val MAX_BUFFER_SIZE = 100

    private val lock = Any()
    private val ringBuffer = ArrayDeque<IslandLogEntry>(MAX_BUFFER_SIZE)

    private val _entriesFlow = MutableStateFlow<List<IslandLogEntry>>(emptyList())
    val entriesFlow = _entriesFlow.asStateFlow()

    private val _selfTestResults = MutableStateFlow<List<SelfTestCheckItem>>(emptyList())
    val selfTestResults = _selfTestResults.asStateFlow()

    fun record(
        seqId: Long,
        action: String,
        presenterTier: String,
        boostPercent: Int,
        jsonSizeBytes: Int,
        isSuccess: Boolean,
        detail: String
    ) {
        val now = System.currentTimeMillis()
        val timeStr = DateFormat.format("HH:mm:ss", Date(now)).toString()
        val entry = IslandLogEntry(
            timestampMs = now,
            formattedTime = timeStr,
            seqId = seqId,
            action = action,
            presenterTier = presenterTier,
            boostPercent = boostPercent,
            jsonSizeBytes = jsonSizeBytes,
            isSuccess = isSuccess,
            detail = detail
        )

        synchronized(lock) {
            if (ringBuffer.size >= MAX_BUFFER_SIZE) {
                ringBuffer.removeFirst()
            }
            ringBuffer.addLast(entry)
            _entriesFlow.value = ringBuffer.toList().asReversed()
        }

        val logLine = "[seq=#$seqId] action=$action tier=$presenterTier boost=$boostPercent% json=${jsonSizeBytes}B ok=$isSuccess: $detail"
        if (isSuccess) {
            Log.i(LOG_TAG, logLine)
        } else {
            Log.w(LOG_TAG, logLine)
        }
    }

    fun updateSelfTestResults(items: List<SelfTestCheckItem>) {
        _selfTestResults.value = items
    }

    fun clear() {
        synchronized(lock) {
            ringBuffer.clear()
            _entriesFlow.value = emptyList()
            _selfTestResults.value = emptyList()
        }
        Log.i(LOG_TAG, "Журнал событий острова очищен пользователем")
    }

    fun formatAsPlainText(): String {
        val list = _entriesFlow.value
        if (list.isEmpty()) return "Журнал событий острова пуст."
        return buildString {
            appendLine("=== HyperBoost Island Event Log (${list.size} записей) ===")
            list.forEach { e ->
                val badge = if (e.isSuccess) "✓" else "✗"
                appendLine("${e.formattedTime} $badge [#${e.seqId}] ${e.action} | ${e.boostPercent}% | ${e.presenterTier} (${e.jsonSizeBytes}B) -> ${e.detail}")
            }
        }
    }
}
