package com.biorate.hygen.lang.highlight

import com.biorate.hygen.lang.HygenFileType
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

/**
 * Exposes the hygen template colors on the *Editor | Color Scheme | Hygen Template* page.
 *
 * The demo text is a real template, so it is highlighted by [HygenSyntaxHighlighter] itself
 * and shows every tag family plus the unterminated-tag error color.
 */
class HygenColorSettingsPage : ColorSettingsPage {

    override fun getDisplayName(): String = HygenFileType.name

    override fun getIcon(): Icon = HygenFileType.icon

    override fun getHighlighter(): SyntaxHighlighter = HygenSyntaxHighlighter()

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey> = emptyMap()

    override fun getDemoText(): String = DEMO_TEXT

    private companion object {
        val DESCRIPTORS: Array<AttributesDescriptor> = arrayOf(
            AttributesDescriptor("Frontmatter//Delimiter", HygenTextAttributes.FRONTMATTER_DELIMITER),
            AttributesDescriptor("Frontmatter//Key", HygenTextAttributes.FRONTMATTER_KEY),
            AttributesDescriptor("Frontmatter//Colon", HygenTextAttributes.FRONTMATTER_COLON),
            AttributesDescriptor("Frontmatter//Value", HygenTextAttributes.FRONTMATTER_VALUE),
            AttributesDescriptor("Frontmatter//Comment", HygenTextAttributes.FRONTMATTER_COMMENT),
            AttributesDescriptor("EJS//Delimiter", HygenTextAttributes.EJS_DELIMITER),
            AttributesDescriptor("EJS//Expression", HygenTextAttributes.EJS_EXPRESSION),
            AttributesDescriptor("EJS//Scriptlet", HygenTextAttributes.EJS_SCRIPTLET),
            AttributesDescriptor("EJS//Comment", HygenTextAttributes.EJS_COMMENT),
            AttributesDescriptor("EJS//Escape", HygenTextAttributes.EJS_ESCAPE),
            AttributesDescriptor("EJS//Unterminated tag", HygenTextAttributes.EJS_UNTERMINATED),
        )

        val DEMO_TEXT: String = """
            |---
            |# hygen template header
            |to: app/<%= name %>.js
            |inject: true
            |---
            |import { <%= Name %> } from './<%- module %>'
            |<% if (withTests) { %>
            |export const <%= name %> = () => {}
            |<% } %>
            |<%# this is a comment tag %>
            |Escaped literal: <%% not a tag %>
            |<%= unterminated tag
        """.trimMargin()
    }
}
