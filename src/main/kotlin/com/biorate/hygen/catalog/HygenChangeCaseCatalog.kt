package com.biorate.hygen.catalog

/**
 * Single source of truth for the `change-case` 3.x helper members exposed to templates as
 * `h.changeCase.*` (design.md D6).
 *
 * Ground truth: [change-case 3.1.0](https://www.npmjs.com/package/change-case/v/3.1.0), the exact
 * version the `@biorate/hygen` fork pins in `packages/@biorate/hygen/package.json`. The fork
 * imports the module's default export and hands it to templates unchanged, so every exported
 * member (the verb-named function and each short alias) is reachable as `h.changeCase.<name>`.
 *
 * The completion contributor (task 6.2) reads from here, and the Spot-check member names in the
 * integrity test pin this list to the dependency so drift is caught.
 */
object HygenChangeCaseCatalog {

    /** Known `h.changeCase.*` member -> short description, in the order exposed by the module. */
    val members: LinkedHashMap<String, String> = linkedMapOf(
        "noCase" to "no case: lowercase words separated by spaces",
        "no" to "alias of noCase",
        "dotCase" to "dot.case",
        "dot" to "alias of dotCase",
        "swapCase" to "sWAP cASE",
        "swap" to "alias of swapCase",
        "pathCase" to "path/case",
        "path" to "alias of pathCase",
        "upperCase" to "UPPERCASE",
        "upper" to "alias of upperCase",
        "lowerCase" to "lowercase",
        "lower" to "alias of lowerCase",
        "camelCase" to "camelCase",
        "camel" to "alias of camelCase",
        "snakeCase" to "snake_case",
        "snake" to "alias of snakeCase",
        "titleCase" to "Title Case",
        "title" to "alias of titleCase",
        "paramCase" to "param-case",
        "param" to "alias of paramCase",
        "kebabCase" to "param-case (alias pairing)",
        "kebab" to "alias of paramCase (kebab-case)",
        "hyphenCase" to "param-case (alias pairing)",
        "hyphen" to "alias of paramCase (hyphen-case)",
        "headerCase" to "Header-Case",
        "header" to "alias of headerCase",
        "pascalCase" to "PascalCase",
        "pascal" to "alias of pascalCase",
        "constantCase" to "CONSTANT_CASE",
        "constant" to "alias of constantCase",
        "sentenceCase" to "Sentence case",
        "sentence" to "alias of sentenceCase",
        "isUpperCase" to "true when the string is upper case",
        "isUpper" to "alias of isUpperCase",
        "isLowerCase" to "true when the string is lower case",
        "isLower" to "alias of isLowerCase",
        "upperCaseFirst" to "Upper first letter only",
        "ucFirst" to "alias of upperCaseFirst",
        "lowerCaseFirst" to "lower first letter only",
        "lcFirst" to "alias of lowerCaseFirst",
    )

    val memberNames: Set<String> get() = members.keys

    fun isKnown(member: String): Boolean = member in members
}
