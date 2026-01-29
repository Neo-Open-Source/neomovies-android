package com.neo.neomovies.data.collaps

object CollapsMpdRewriter {
    fun rewrite(mpd: String, voices: List<String>, subtitles: List<CollapsRepository.CollapsSubtitle> = emptyList()): String {
        if (mpd.isBlank()) return mpd

        val audioSetRegex = Regex(
            "(?is)<AdaptationSet\\b[^>]*\\bcontentType=\\\"audio\\\"[^>]*>.*?</AdaptationSet>",
        )

        val rewrittenAudio = audioSetRegex.replace(mpd) { m ->
            val block = m.value
            val openTagMatch = Regex("(?is)^<AdaptationSet\\b[^>]*>").find(block) ?: return@replace block
            val openTag = openTagMatch.value

            val langMatch = Regex("\\blang=\\\"([^\\\"]+)\\\"", RegexOption.IGNORE_CASE).find(openTag)
            val rawLang = langMatch?.groupValues?.getOrNull(1)

            val idx = rawLang
                ?.let { Regex("^ru(\\d+)$", RegexOption.IGNORE_CASE).find(it) }
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()

            val voiceName = idx?.let { voices.getOrNull(it) }
            val fallbackLabel = idx?.let { "Audio ${it + 1}" }

            val normalizedLang = when {
                !voiceName.isNullOrBlank() &&
                    (voiceName.contains("eng", ignoreCase = true) || voiceName.contains("original", ignoreCase = true)) -> "en"
                !voiceName.isNullOrBlank() -> "ru"
                // If Collaps uses ru0/ru1/ru2... but didn't provide names, at least show proper language.
                rawLang != null && Regex("^ru\\d+$", RegexOption.IGNORE_CASE).matches(rawLang) -> "ru"
                rawLang != null && Regex("^en\\d+$", RegexOption.IGNORE_CASE).matches(rawLang) -> "en"
                else -> rawLang
            }

            var newOpenTag = openTag
            if (!normalizedLang.isNullOrBlank()) {
                newOpenTag =
                    if (langMatch != null) {
                        newOpenTag.replace(langMatch.value, "lang=\"$normalizedLang\"")
                    } else {
                        newOpenTag.replace(">", " lang=\"$normalizedLang\">")
                    }
            }

            val labelToUse = voiceName?.takeIf { it.isNotBlank() } ?: fallbackLabel
            if (!labelToUse.isNullOrBlank()) {
                val labelAttrRegex = Regex("\\blabel=\\\"[^\\\"]*\\\"", RegexOption.IGNORE_CASE)
                val labelAttr = "label=\"${escapeXml(labelToUse)}\""
                newOpenTag =
                    if (labelAttrRegex.containsMatchIn(newOpenTag)) {
                        labelAttrRegex.replaceFirst(newOpenTag, labelAttr)
                    } else {
                        newOpenTag.replace(">", " $labelAttr>")
                    }
            }

            var newBlock = block.replaceFirst(openTag, newOpenTag)
            if (!labelToUse.isNullOrBlank()) {
                val labelRegex = Regex("(?is)<Label\\b[^>]*>.*?</Label>")
                newBlock =
                    if (labelRegex.containsMatchIn(newBlock)) {
                        labelRegex.replaceFirst(newBlock, "<Label>${escapeXml(labelToUse)}</Label>")
                    } else {
                        newBlock.replaceFirst(newOpenTag, newOpenTag + "<Label>${escapeXml(labelToUse)}</Label>")
                    }
            }

            newBlock
        }

        if (subtitles.isEmpty()) return rewrittenAudio

        val subsXml = buildSubtitlesAdaptationSets(subtitles)
        if (subsXml.isBlank()) return rewrittenAudio

        val periodClose = Regex("</Period>", RegexOption.IGNORE_CASE)
        return if (periodClose.containsMatchIn(rewrittenAudio)) {
            periodClose.replaceFirst(rewrittenAudio, subsXml + "</Period>")
        } else {
            rewrittenAudio
        }
    }

    private fun buildSubtitlesAdaptationSets(subtitles: List<CollapsRepository.CollapsSubtitle>): String {
        val sb = StringBuilder()
        subtitles.forEachIndexed { idx, s ->
            val lang = s.lang.ifBlank { "ru" }
            val label = s.label.ifBlank { "Subtitle" }
            val url = s.url
            if (url.isBlank()) return@forEachIndexed

            val mimeType = detectSubtitleMimeType(url)

            sb.append("\n")
            sb.append("<AdaptationSet contentType=\"text\" lang=\"")
            sb.append(escapeXml(lang))
            sb.append("\">")
            sb.append("<Label>")
            sb.append(escapeXml(label))
            sb.append("</Label>")
            sb.append("<Representation id=\"sub")
            sb.append(idx)
            sb.append("\" mimeType=\"")
            sb.append(escapeXml(mimeType))
            sb.append("\" bandwidth=\"256\">")
            sb.append("<BaseURL>")
            sb.append(escapeXml(url))
            sb.append("</BaseURL>")
            sb.append("</Representation>")
            sb.append("</AdaptationSet>")
        }
        return sb.toString()
    }

    private fun detectSubtitleMimeType(url: String): String {
        val noQuery = url.substringBefore('?').substringBefore('#')
        val ext = noQuery.substringAfterLast('.', missingDelimiterValue = "").lowercase()

        return when (ext) {
            "vtt" -> "text/vtt"
            "srt" -> "application/x-subrip"
            "ass", "ssa" -> "text/x-ssa"
            "ttml", "xml" -> "application/ttml+xml"
            else -> "text/vtt"
        }
    }

    private fun escapeXml(s: String): String {
        return buildString(s.length) {
            for (ch in s) {
                when (ch) {
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '&' -> append("&amp;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(ch)
                }
            }
        }
    }
}
