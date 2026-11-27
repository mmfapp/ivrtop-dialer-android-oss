package com.ivrtop.dialer.core.common.phone

/**
 * T9 matching for Latin AND Hebrew names.
 * Hebrew mapping follows the Israeli handset convention (2=דהו, 3=אבג, 4=מםנן, 5=יכךל, 6=זחט,
 * 7=רשת, 8=צץקף, 9=סעפ ...). Keys with no letters (0,1) match only digits.
 */
object T9 {
    private val latin = mapOf(
        '2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl",
        '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz",
    )
    private val hebrew = mapOf(
        '2' to "דהו", '3' to "אבג", '4' to "מםנן", '5' to "יכךל",
        '6' to "זחט", '7' to "רשת", '8' to "צץק", '9' to "סעפף",
    )
    private val charToDigit: Map<Char, Char> = buildMap {
        latin.forEach { (d, letters) -> letters.forEach { put(it, d) } }
        hebrew.forEach { (d, letters) -> letters.forEach { put(it, d) } }
    }

    /** Letters shown under each dial-pad key. */
    fun lettersFor(digit: Char, includeHebrew: Boolean): String {
        val l = latin[digit]?.uppercase().orEmpty()
        val h = if (includeHebrew) hebrew[digit].orEmpty() else ""
        return listOf(h, l).filter { it.isNotEmpty() }.joinToString(" ")
    }

    fun encode(name: String): String = buildString {
        name.lowercase().forEach { c ->
            when {
                c.isDigit() -> append(c)
                c in charToDigit -> append(charToDigit.getValue(c))
                else -> append(' ')
            }
        }
    }

    /**
     * Returns match score or -1. Matches whole-name prefix or the start of any word,
     * plus initials ("ישראל ישראלי" ↔ 55 for initials).
     */
    fun score(name: String, query: String): Int {
        if (query.isEmpty()) return -1
        val encoded = encode(name)
        val words = encoded.split(' ').filter { it.isNotEmpty() }
        if (encoded.replace(" ", "").startsWith(query)) return 100
        words.forEachIndexed { i, w -> if (w.startsWith(query)) return 80 - i }
        val initials = words.joinToString("") { it.first().toString() }
        if (initials.startsWith(query)) return 60
        if (encoded.replace(" ", "").contains(query)) return 30
        return -1
    }
}
