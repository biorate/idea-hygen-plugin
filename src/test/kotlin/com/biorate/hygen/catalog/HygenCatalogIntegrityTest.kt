package com.biorate.hygen.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Integrity checks on the shared catalog objects (design.md D6, task 6.1):
 * every catalog is non-empty, has no blank/duplicate member names, and each entry carries a
 * human-readable description. A handful of spot-checks pin the catalogs to the ground-truth
 * dependency (`change-case` 3.1.0 and `inflection` 1.12.0 in `@biorate/hygen`).
 */
class HygenCatalogIntegrityTest {

    private fun <K, V> assertNoBlankNames(map: Map<K, V>, what: String) {
        map.keys.forEach { key ->
            assertFalse("$what name must not be blank: '$key'", key.toString().isBlank())
        }
    }

    private fun <K, V> assertNoDuplicateNames(map: Map<K, V>, what: String) {
        assertTrue("$what names must be unique", map.keys.size == map.keys.toSet().size)
    }

    private fun assertNonEmptyDescription(map: Map<String, String>, what: String) {
        map.forEach { (name, description) ->
            assertFalse("$what '$name' must have a non-blank description", description.isBlank())
        }
    }

    // ---- frontmatter keys ----

    @Test
    fun `frontmatter catalog is non-empty`() {
        assertTrue(HygenFrontmatterCatalog.keys.isNotEmpty())
    }

    @Test
    fun `frontmatter catalog has no blank or duplicate keys`() {
        assertNoBlankNames(HygenFrontmatterCatalog.keys, "frontmatter key")
        assertNoDuplicateNames(HygenFrontmatterCatalog.keys, "frontmatter key")
    }

    @Test
    fun `every frontmatter key has a description`() {
        assertNonEmptyDescription(HygenFrontmatterCatalog.keys, "frontmatter key")
    }

    @Test
    fun `frontmatter catalog exposes the canonical core keys`() {
        for (key in listOf("to", "inject", "sh", "before", "after", "prepend", "append", "at_line", "skip_if", "unless_exists", "local")) {
            assertTrue("expected core frontmatter key '$key'", HygenFrontmatterCatalog.isKnown(key))
        }
    }

    @Test
    fun `frontmatter catalog exposes the fork-added keys`() {
        for (key in listOf("setup", "force", "message", "echo", "spinner", "sh_ignore_exit", "mode", "verbose", "from", "eof_last")) {
            assertTrue("expected fork frontmatter key '$key'", HygenFrontmatterCatalog.isKnown(key))
        }
        assertTrue(HygenFrontmatterCatalog.description("setup")!!.isNotBlank())
    }

    // ---- change-case helpers ----

    @Test
    fun `changeCase catalog is non-empty and matches change-case 3_1_0 surface`() {
        assertTrue(HygenChangeCaseCatalog.members.isNotEmpty())
        // 20 verb-named functions + their 20 short aliases = 40 members on the module object.
        assertEquals(40, HygenChangeCaseCatalog.members.size)
    }

    @Test
    fun `changeCase catalog has no blank or duplicate members`() {
        assertNoBlankNames(HygenChangeCaseCatalog.members, "changeCase member")
        assertNoDuplicateNames(HygenChangeCaseCatalog.members, "changeCase member")
    }

    @Test
    fun `every changeCase member has a description`() {
        assertNonEmptyDescription(HygenChangeCaseCatalog.members, "changeCase member")
    }

    @Test
    fun `changeCase spot-checks canonical members`() {
        for (member in listOf("paramCase", "camelCase", "snakeCase", "pascalCase", "constantCase", "kebabCase", "noCase", "sentenceCase")) {
            assertTrue("expected changeCase member '$member'", HygenChangeCaseCatalog.isKnown(member))
        }
    }

    @Test
    fun `changeCase exposes the short aliases`() {
        for (alias in listOf("param", "kebab", "hyphen", "camel", "snake", "pascal", "constant", "no", "title", "upper", "lower", "ucFirst", "lcFirst", "isUpper", "isLower")) {
            assertTrue("expected changeCase alias '$alias'", HygenChangeCaseCatalog.isKnown(alias))
        }
    }

    // ---- inflection helpers ----

    @Test
    fun `inflection catalog is non-empty and matches inflection 1_12_0 string transforms`() {
        assertTrue(HygenInflectionCatalog.members.isNotEmpty())
        // 15 base transforms + the fork's undasherize extension.
        assertEquals(16, HygenInflectionCatalog.members.size)
    }

    @Test
    fun `inflection catalog has no blank or duplicate members`() {
        assertNoBlankNames(HygenInflectionCatalog.members, "inflection member")
        assertNoDuplicateNames(HygenInflectionCatalog.members, "inflection member")
    }

    @Test
    fun `every inflection member has a description`() {
        assertNonEmptyDescription(HygenInflectionCatalog.members, "inflection member")
    }

    @Test
    fun `inflection spot-checks canonical members`() {
        for (member in listOf("pluralize", "singularize", "inflect", "camelize", "underscore", "humanize", "capitalize", "dasherize", "titleize", "demodulize", "tableize", "classify", "foreign_key", "ordinalize", "transform")) {
            assertTrue("expected inflection member '$member'", HygenInflectionCatalog.isKnown(member))
        }
    }

    @Test
    fun `inflection exposes the fork undasherize extension`() {
        assertTrue(HygenInflectionCatalog.isKnown("undasherize"))
    }

    // ---- cross-catalog ----

    @Test
    fun `catalog names are disjoint between changeCase and inflection`() {
        val overlap = HygenChangeCaseCatalog.memberNames.intersect(HygenInflectionCatalog.memberNames)
        assertTrue("helper names must not overlap between catalogs: $overlap", overlap.isEmpty())
    }

    @Test
    fun `frontmatter keys do not collide with helper names`() {
        val overlap = HygenFrontmatterCatalog.knownKeys
            .intersect(HygenChangeCaseCatalog.memberNames + HygenInflectionCatalog.memberNames)
        assertTrue("frontmatter keys must not collide with helper names: $overlap", overlap.isEmpty())
    }
}
