package com.biorate.hygen.catalog

/**
 * Single source of truth for the `inflection` 1.x helper members exposed to templates as
 * `h.inflection.*` (design.md D6).
 *
 * Ground truth: [inflection 1.12.0](https://www.npmjs.com/package/inflection/v/1.12.0), the exact
 * version the `@biorate/hygen` fork pins in `packages/@biorate/hygen/package.json`. The fork
 * hands the module's default export to templates unchanged, so every public string-transform
 * member is reachable as `h.inflection.<name>`, plus `undasherize`, a kebab-to-Pascal extension the
 * fork adds in `src/helpers.ts`.
 *
 * Not listed: `indexOf` (array utility, not a string transformation) and `version` (a data
 * property, not a callable). The completion contributor (task 6.2) reads from here.
 */
object HygenInflectionCatalog {

    /** Known `h.inflection.*` member -> short description, in the order the module exposes them. */
    val members: LinkedHashMap<String, String> = linkedMapOf(
        "pluralize" to "return the plural form of a noun",
        "singularize" to "return the singular form of a noun",
        "inflect" to "pluralize or singularize based on a count",
        "camelize" to "underscored words to camel case (/, -> ::)",
        "underscore" to "camel case words to lower_case (:: -> /)",
        "humanize" to "underscored words to humanized text",
        "capitalize" to "upper first letter, lower the rest",
        "dasherize" to "replace spaces/underscores with dashes",
        "titleize" to "capitalize as a book title",
        "demodulize" to "strip module path, keep the class name",
        "tableize" to "camel cased words to underscored plural",
        "classify" to "underscored plural to camel cased singular",
        "foreign_key" to "append an _id foreign key suffix",
        "ordinalize" to "add an ordinal suffix (1st, 2nd, 3rd)",
        "transform" to "apply a chain of inflection methods",
        "undasherize" to "kebab/snake case to PascalCase (fork extension)",
    )

    val memberNames: Set<String> get() = members.keys

    fun isKnown(member: String): Boolean = member in members
}
