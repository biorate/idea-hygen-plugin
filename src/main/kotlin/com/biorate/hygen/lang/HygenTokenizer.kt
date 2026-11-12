package com.biorate.hygen.lang

/** One lexed token: element type plus absolute offsets in the buffer. */
data class HygenToken(val type: HygenElementType, val start: Int, val end: Int)

/**
 * Single state-machine lexer for the whole template file (design.md D2):
 *
 *     START -> FRONTMATTER_DELIMITER? -> FRONTMATTER -> FRONTMATTER_DELIMITER -> BODY
 *
 * The frontmatter region and the body share the same EJS island tokenizer, because both
 * layers are rendered by the same EJS engine.
 */
object HygenTokenizer {

    fun tokenize(text: CharSequence): List<HygenToken> {
        val tokens = ArrayList<HygenToken>()
        val length = text.length
        var bodyStart = 0

        val closingDelimiter = findClosingDelimiter(text)
        if (closingDelimiter != null) {
            tokenizeFrontmatter(text, 0, closingDelimiter.first, tokens)
            bodyStart = closingDelimiter.second
        }
        tokenizeBody(text, bodyStart, length, tokens)
        return tokens
    }

    /**
     * True when the text starts with a `---` delimiter line but has no matching closing `---` line.
     * Used by the broken-frontmatter-delimiter inspection; the lexer leaves such a run as body text.
     */
    fun hasUnterminatedFrontmatter(text: CharSequence): Boolean {
        val firstLineEnd = lineEnd(text, 0)
        return isDelimiterLine(text, 0, firstLineEnd) && findClosingDelimiter(text) == null
    }

    /**
     * Returns (opening delimiter end, closing delimiter end) when the file starts with a
     * `---` line and has a matching closing `---` line; null otherwise.
     */
    private fun findClosingDelimiter(text: CharSequence): Pair<Int, Int>? {
        val firstLineEnd = lineEnd(text, 0)
        if (!isDelimiterLine(text, 0, firstLineEnd)) return null
        var pos = firstLineEnd
        while (pos < text.length) {
            val end = lineEnd(text, pos)
            if (isDelimiterLine(text, pos, end)) {
                return pos to end
            }
            pos = end
        }
        return null
    }

    /**
     * When the buffer begins with a `---` delimiter line but no matching closing `---` line
     * exists at the same nesting level (a broken/opened frontmatter block), returns the
     * [start, end) offset range of that opening delimiter; null otherwise.
     *
     * The parser only builds a `HYGEN_FRONTMATTER` node when a closing delimiter exists, so an
     * opening `---` that never closes is otherwise lost among body text. This helper recovers it
     * for the broken-frontmatter-delimiter inspection.
     */
    private fun isDelimiterLine(text: CharSequence, start: Int, end: Int): Boolean {
        var s = start
        var e = end
        while (s < e && (text[s] == ' ' || text[s] == '\t')) s++
        while (e > s && (text[e - 1] == '\n' || text[e - 1] == '\r')) e--
        return e - s == 3 && text[s] == '-' && text[s + 1] == '-' && text[s + 2] == '-'
    }

    /** End offset of the line starting at [start], including the line terminator. */
    private fun lineEnd(text: CharSequence, start: Int): Int {
        var i = start
        while (i < text.length && text[i] != '\n') i++
        return if (i < text.length) i + 1 else i
    }

    private fun tokenizeFrontmatter(
        text: CharSequence,
        start: Int,
        contentEnd: Int,
        tokens: MutableList<HygenToken>,
    ) {
        // opening delimiter line
        var pos = 0
        var end = lineEnd(text, pos)
        val openTrim = trimLineTerminator(text, pos, end)
        tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_DELIMITER, pos, openTrim))
        // The terminator is trimmed off the delimiter token; re-emit it so the token stream
        // stays contiguous (the editor highlighter rejects lexers that leave untokenized gaps).
        if (end > openTrim) {
            tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_WHITESPACE, openTrim, end))
        }
        pos = end

        while (pos < contentEnd) {
            val lineEndOffset = minOf(lineEnd(text, pos), contentEnd)
            tokenizeFrontmatterLine(text, pos, lineEndOffset, tokens)
            pos = lineEndOffset
        }

        // closing delimiter line
        val closeStart = contentEnd
        val closeEnd = lineEnd(text, closeStart)
        val closeTrim = trimLineTerminator(text, closeStart, minOf(closeEnd, text.length))
        tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_DELIMITER, closeStart, closeTrim))
        if (closeEnd > closeTrim) {
            tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_WHITESPACE, closeTrim, closeEnd))
        }
    }

    private fun trimLineTerminator(text: CharSequence, start: Int, end: Int): Int {
        var e = end
        while (e > start && (text[e - 1] == '\n' || text[e - 1] == '\r')) e--
        return e
    }

    private fun tokenizeFrontmatterLine(
        text: CharSequence,
        start: Int,
        end: Int,
        tokens: MutableList<HygenToken>,
    ) {
        var i = start
        val contentEnd = trimLineTerminator(text, start, end)

        // leading whitespace
        while (i < contentEnd && (text[i] == ' ' || text[i] == '\t')) i++
        if (i > start) tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_WHITESPACE, start, i))
        if (i >= contentEnd) {
            if (end > contentEnd) tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_WHITESPACE, contentEnd, end))
            return
        }

        // whole-line comment
        if (text[i] == '#') {
            tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_COMMENT, i, contentEnd))
            if (end > contentEnd) tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_WHITESPACE, contentEnd, end))
            return
        }

        // `key:` at the beginning of a line
        val keyStart = i
        while (i < contentEnd && isKeyChar(text[i])) i++
        var keyEnd = i
        while (keyEnd < contentEnd && (text[keyEnd] == ' ' || text[keyEnd] == '\t')) keyEnd++
        if (keyEnd < contentEnd && text[keyEnd] == ':' && keyEnd > keyStart) {
            tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_KEY, keyStart, i))
            if (keyEnd > i) tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_WHITESPACE, i, keyEnd))
            tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_COLON, keyEnd, keyEnd + 1))
            var valueStart = keyEnd + 1
            while (valueStart < contentEnd && (text[valueStart] == ' ' || text[valueStart] == '\t')) valueStart++
            if (valueStart > keyEnd + 1) {
                tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_WHITESPACE, keyEnd + 1, valueStart))
            }
            tokenizeValue(text, valueStart, contentEnd, tokens)
        } else {
            // Continuation line or unparsed value text (no colon). The scanned prefix [keyStart, i)
            // is *not* a key, so the value must start at keyStart — starting at `i` would leave that
            // prefix untokenized and break the contiguous token stream the editor requires.
            tokenizeValue(text, keyStart, contentEnd, tokens)
        }

        if (end > contentEnd) tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_WHITESPACE, contentEnd, end))
    }

    private fun isKeyChar(c: Char): Boolean = c.isLetterOrDigit() || c == '_' || c == '-' || c == '.'

    /** Frontmatter values may contain EJS islands, so the shared tag scanner is used. */
    private fun tokenizeValue(text: CharSequence, start: Int, end: Int, tokens: MutableList<HygenToken>) {
        var pos = start
        while (pos < end) {
            val open = indexOfTag(text, pos, end)
            if (open < 0) {
                if (pos < end) tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_VALUE, pos, end))
                return
            }
            if (open > pos) tokens.add(HygenToken(HygenTokenTypes.FRONTMATTER_VALUE, pos, open))
            pos = tokenizeTag(text, open, end, tokens)
        }
    }

    private fun tokenizeBody(text: CharSequence, start: Int, end: Int, tokens: MutableList<HygenToken>) {
        var pos = start
        while (pos < end) {
            val open = indexOfTag(text, pos, end)
            if (open < 0) {
                tokens.add(HygenToken(HygenTokenTypes.BODY_TEXT, pos, end))
                return
            }
            if (open > pos) tokens.add(HygenToken(HygenTokenTypes.BODY_TEXT, pos, open))
            pos = tokenizeTag(text, open, end, tokens)
        }
    }

    private fun indexOfTag(text: CharSequence, from: Int, end: Int): Int {
        var i = from
        while (i + 1 < end) {
            if (text[i] == '<' && text[i + 1] == '%') return i
            i++
        }
        return -1
    }

    /**
     * Tokenizes one EJS island starting at [open] and returns the offset right after it.
     * Supports `<%= %>`, `<%- %>`, `<% %>`, `<%# %>`, the `<%%` literal escape, and the
     * `-` trimming marker before the closing delimiter.
     */
    private fun tokenizeTag(text: CharSequence, open: Int, end: Int, tokens: MutableList<HygenToken>): Int {
        // `<%%` is a literal escape sequence, not a tag
        if (open + 2 < end && text[open + 2] == '%') {
            tokens.add(HygenToken(HygenTokenTypes.EJS_ESCAPE, open, open + 3))
            return open + 3
        }

        var openEnd = open + 2
        val marker = if (openEnd < end) text[openEnd] else null
        val isTagMarker = marker == '=' || marker == '-' || marker == '#'
        if (isTagMarker) openEnd++

        val close = findClose(text, openEnd, end)
        if (close < 0) {
            tokens.add(HygenToken(HygenTokenTypes.EJS_UNTERMINATED, open, end))
            return end
        }

        tokens.add(HygenToken(HygenTokenTypes.EJS_OPEN_DELIMITER, open, openEnd))
        val contentType = when (marker) {
            '=', '-' -> HygenTokenTypes.EJS_EXPRESSION
            '#' -> HygenTokenTypes.EJS_COMMENT
            else -> HygenTokenTypes.EJS_SCRIPTLET
        }
        // a trailing `-` before `%>` is the trimming marker, not content
        var contentEnd = close
        if (contentEnd > openEnd && text[contentEnd - 1] == '-') contentEnd--
        if (contentEnd > openEnd) tokens.add(HygenToken(contentType, openEnd, contentEnd))
        tokens.add(HygenToken(HygenTokenTypes.EJS_CLOSE_DELIMITER, contentEnd, close + 2))
        return close + 2
    }

    private fun findClose(text: CharSequence, from: Int, end: Int): Int {
        var i = from
        while (i + 1 < end) {
            if (text[i] == '%' && text[i + 1] == '>') return i
            i++
        }
        return -1
    }
}
