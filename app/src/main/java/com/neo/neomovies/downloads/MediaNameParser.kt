package com.neo.neomovies.downloads

object MediaNameParser {
    fun parseSeasonEpisode(name: String): Pair<Int, Int>? {
        val patterns = listOf(
            "(?i)\\bS(\\d{1,2})\\s*[._-]?\\s*E(\\d{1,3})\\b",
            "(?i)\\b(\\d{1,2})\\s*[xX]\\s*(\\d{1,3})\\b",
            "(?i)season\\s*(\\d{1,2}).*episode\\s*(\\d{1,3})"
        )
        for (pattern in patterns) {
            val m = Regex(pattern).find(name) ?: continue
            val s = m.groupValues.getOrNull(1)?.toIntOrNull()
            val e = m.groupValues.getOrNull(2)?.toIntOrNull()
            if (s != null && e != null) return s to e
        }
        return null
    }
}
