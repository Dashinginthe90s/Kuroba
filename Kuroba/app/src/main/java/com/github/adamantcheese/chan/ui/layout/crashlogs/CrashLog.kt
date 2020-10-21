package com.github.adamantcheese.chan.ui.layout.crashlogs

import java.io.File
import java.util.*

data class CrashLog(val file: File, val fileName: String, var markedToSend: Boolean) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CrashLog

        if (fileName != other.fileName) return false
        if (markedToSend != other.markedToSend) return false

        return true
    }

    override fun hashCode(): Int {
        return Objects.hash(fileName, markedToSend)
    }
}