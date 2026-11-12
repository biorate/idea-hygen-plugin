package com.biorate.hygen.lang

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Platform lexer adapter over [HygenTokenizer]. The whole buffer is tokenized eagerly on
 * [start]; templates are small, and this keeps the state machine trivially testable.
 */
class HygenLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var tokens: List<HygenToken> = emptyList()
    private var index = -1
    private var bufferEnd = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.tokens = HygenTokenizer.tokenize(buffer.subSequence(0, endOffset))
        this.index = when {
            tokens.isEmpty() -> 0
            startOffset <= 0 -> 0
            else -> tokens.indexOfFirst { it.end > startOffset }.let { if (it < 0) tokens.size else it }
        }
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? = tokens.getOrNull(index)?.type

    override fun getTokenStart(): Int = tokens.getOrNull(index)?.start ?: bufferEnd

    override fun getTokenEnd(): Int = tokens.getOrNull(index)?.end ?: bufferEnd

    override fun advance() {
        index++
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd
}
