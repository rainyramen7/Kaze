package com.kaze.app.crypto

object FileSafety {
    const val MAX_AUTO_ACCEPT_BYTES: Long = 100L * 1024L * 1024L

    val dangerousExtensions: Set<String> = setOf(
        "apk", "apks", "xapk", "sh", "zsh", "bash", "command",
        "dmg", "pkg", "app", "exe", "bat", "msi", "jar", "dex", "so"
    )

    fun sanitizeFileName(raw: String): String {
        val tail = raw.split('/', '\\').lastOrNull().orEmpty()
        val noControl = tail.filter { ch -> ch.code >= 0x20 && ch.code != 0x7F }
        var cleaned = noControl.dropWhile { it == '.' || it == ' ' }.trim()
        cleaned = cleaned.replace(Regex("[:/\\\\?%*|\"<>]"), "_")
        if (cleaned.isEmpty() || cleaned == "." || cleaned == "..") {
            return "kaze_file"
        }
        if (cleaned.length > 140) {
            val dot = cleaned.lastIndexOf('.')
            if (dot > 0 && cleaned.length - dot - 1 in 1..16) {
                val ext = cleaned.substring(dot)
                val base = cleaned.substring(0, 140 - ext.length)
                return "$base$ext"
            }
            return cleaned.take(140)
        }
        return cleaned
    }

    fun requiresConfirmation(fileName: String, sizeBytes: Long): Boolean {
        val clean = sanitizeFileName(fileName)
        val dot = clean.lastIndexOf('.')
        val ext = if (dot > 0) clean.substring(dot + 1).lowercase() else ""
        if (ext in dangerousExtensions) return true
        if (sizeBytes > MAX_AUTO_ACCEPT_BYTES) return true
        return false
    }
}
