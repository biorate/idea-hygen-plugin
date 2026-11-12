package com.biorate.hygen.completion

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

/**
 * Completion for hygen templates (design.md D6, task 6.2): frontmatter keys and `h` helper members
 * inside EJS islands. All position routing and candidate production is delegated to the pure
 * [HygenCompletion] object; this contributor only adapts the platform completion contract to the
 * lookup model, so the testable logic is the exact production path.
 *
 * Registered in `plugin.xml` as a `completion.contributor` for the `HygenTemplate` language. The
 * registration pattern matches any element and the provider gates internally on the active
 * language and position, so injected host-language ranges are left untouched.
 */
class HygenCompletionContributor : CompletionContributor() {

    init {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement(), HygenCompletionProvider())
    }
}

/** [com.intellij.codeInsight.completion.CompletionProvider] driving [HygenCompletion]. */
class HygenCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
    ) {
        val file = parameters.position.containingFile as? HygenFile ?: return
        if (file.language != HygenLanguage) return

        for (proposal in HygenCompletion.computeProposals(file, parameters.offset)) {
            result.addElement(
                LookupElementBuilder.create(proposal.name)
                    .withTypeText(proposal.description),
            )
        }
    }
}
