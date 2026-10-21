package com.xeroxphotopro.core

object ExportName {
    /** Keep international names readable while excluding path separators and control characters. */
    fun prefix(name: String): String {
        val safe = name.trim().replace(Regex("[^\\p{L}\\p{M}\\p{N}_-]+"), "-").take(60).trim('-','_')
        return if (safe.isEmpty()) "XeroxPhotoPro-" else "XeroxPhotoPro-$safe-"
    }
}
