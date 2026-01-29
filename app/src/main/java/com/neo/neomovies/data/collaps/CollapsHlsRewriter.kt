package com.neo.neomovies.data.collaps

object CollapsHlsRewriter {
    fun rewrite(
        master: String,
        voices: List<String>,
        subtitles: List<CollapsRepository.CollapsSubtitle> = emptyList(),
    ): String {
        if (master.isBlank()) return master

        val lines = master.replace("\r\n", "\n").replace("\r", "\n").split("\n")
        val out = ArrayList<String>(lines.size + 32)

        val subsGroupId = "subs0"

        // Collaps master playlists often contain duplicate renditions/variants for failover.
        // Keep the primary groups only to avoid duplicated audio tracks in Exo track selection.
        val filteredLines = ArrayList<String>(lines.size)
        val seenVariantKeys = HashSet<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.startsWith("#EXT-X-MEDIA", ignoreCase = true)) {
                val groupId = extractQuotedAttr(line, "GROUP-ID")
                if (groupId != null && groupId.startsWith("failover-", ignoreCase = true)) {
                    i++
                    continue
                }
                filteredLines += line
                i++
                continue
            }

            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                val audioGroup = extractQuotedAttr(line, "AUDIO")
                if (audioGroup != null && audioGroup.startsWith("failover-", ignoreCase = true)) {
                    // Skip the following URI line as well.
                    i += 2
                    continue
                }

                val resolution = extractAttrValue(line, "RESOLUTION")
                val codecs = extractQuotedAttr(line, "CODECS")
                val key = listOfNotNull(resolution, codecs, audioGroup).joinToString("|")
                if (key.isNotBlank() && !seenVariantKeys.add(key)) {
                    // Duplicate variant (usually another host). Skip it + following URI line.
                    i += 2
                    continue
                }

                filteredLines += line
                // Copy the following URI line if present.
                if (i + 1 < lines.size) {
                    filteredLines += lines[i + 1]
                }
                i += 2
                continue
            }

            filteredLines += line
            i++
        }

        val activeLines = filteredLines

        // Prepend everything before first STREAM-INF, while rewriting AUDIO renditions.
        val activeStreamInfIdx = activeLines.indexOfFirst { it.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) }
        val headEnd = if (activeStreamInfIdx >= 0) activeStreamInfIdx else activeLines.size
        for (i in 0 until headEnd) {
            out += rewriteMediaLine(activeLines[i], voices)
        }

        // Inject subtitles renditions (best-effort).
        if (subtitles.isNotEmpty()) {
            for (s in subtitles) {
                val lang = s.lang.ifBlank { "ru" }
                val label = s.label.ifBlank { "Subtitle" }
                val uri = s.url
                if (uri.isBlank()) continue

                out +=
                    "#EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID=\"$subsGroupId\",NAME=\"${escapeAttr(label)}\",DEFAULT=NO,AUTOSELECT=YES,LANGUAGE=\"${escapeAttr(lang)}\",URI=\"${escapeAttr(uri)}\""
            }
        }

        // Copy the rest, adding SUBTITLES group to STREAM-INF if we injected subtitles.
        for (i in headEnd until activeLines.size) {
            val line = activeLines[i]
            if (subtitles.isNotEmpty() && line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                out += addOrReplaceAttribute(line, "SUBTITLES", subsGroupId)
            } else {
                out += line
            }
        }

        return out.joinToString("\n")
    }

    private fun rewriteMediaLine(line: String, voices: List<String>): String {
        if (!line.startsWith("#EXT-X-MEDIA", ignoreCase = true)) return line
        if (!line.contains("TYPE=AUDIO", ignoreCase = true)) return line

        val nameMatch = Regex("\\bNAME=\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE).find(line)
        val rawName = nameMatch?.groupValues?.getOrNull(1)

        val idx = rawName
            ?.let { Regex("^(?:rus|ru)(\\d+)$", RegexOption.IGNORE_CASE).find(it) }
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()

        val voiceName = idx?.let { voices.getOrNull(it) }

        val normalizedLang = when {
            voiceName == null -> null
            voiceName.contains("eng", ignoreCase = true) || voiceName.contains("original", ignoreCase = true) -> "en"
            else -> "ru"
        }

        var out = line
        if (!voiceName.isNullOrBlank()) {
            out = addOrReplaceAttribute(out, "NAME", voiceName)
        }
        if (!normalizedLang.isNullOrBlank()) {
            out = addOrReplaceAttribute(out, "LANGUAGE", normalizedLang)
        }
        return out
    }

    private fun addOrReplaceAttribute(line: String, key: String, value: String): String {
        val attrRegex = Regex("\\b" + Regex.escape(key) + "=\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
        val escapedValue = escapeAttr(value)
        val newAttr = "$key=\"$escapedValue\""

        return if (attrRegex.containsMatchIn(line)) {
            attrRegex.replaceFirst(line, newAttr)
        } else {
            // Insert right after the tag name if possible.
            if (line.contains(":")) {
                val prefix = line.substringBefore(':')
                val rest = line.substringAfter(':')
                "$prefix:$newAttr,$rest"
            } else {
                "$line,$newAttr"
            }
        }
    }

    private fun extractQuotedAttr(line: String, key: String): String? {
        val m = Regex("\\b" + Regex.escape(key) + "=\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE).find(line)
        return m?.groupValues?.getOrNull(1)
    }

    private fun extractAttrValue(line: String, key: String): String? {
        val m = Regex("\\b" + Regex.escape(key) + "=([^,\\s]+)", RegexOption.IGNORE_CASE).find(line)
        return m?.groupValues?.getOrNull(1)
    }

    private fun escapeAttr(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
