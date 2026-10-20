package com.printassist.android

object ExportNames {
    fun base(name: String): String {
        val clean = name.trim().replace(Regex("[\\s]+"), "_").replace(Regex("[^\\p{L}\\p{N}_.-]"), "_").trim('.', '_').take(80)
        return clean.ifEmpty { "Document" }.let {
            val reserved = Regex("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")
            if (reserved.matches(it.substringBefore('.').uppercase(java.util.Locale.ROOT))) "Customer_$it" else it
        }
    }
    fun file(base: String, kind: String, page: Int = 0) = when(kind) {
        "original" -> "${base}_original.pdf"
        "report" -> "${base}_verification.txt"
        "png", "jpg" -> "${base}_page_${page+1}.$kind"
        else -> "$base.pdf"
    }
}
