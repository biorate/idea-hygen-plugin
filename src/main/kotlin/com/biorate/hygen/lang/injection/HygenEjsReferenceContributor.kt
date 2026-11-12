package com.biorate.hygen.lang.injection

import com.biorate.hygen.lang.psi.HygenEjsIsland
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.PsiReferenceProvider
import com.intellij.patterns.PlatformPatterns

/**
 * Registers the EJS-island reference provider for the injected JavaScript language (design.md D4
 * fallback, spec: synthetic declarations for the template context).
 *
 * Registered in `plugin.xml` for `language="JavaScript"`, it fires inside injected JS fragments.
 * The provider ([HygenEjsReferenceProvider]) is gated to injected fragments that originate from a
 * [HygenEjsIsland], so it never affects ordinary JavaScript: outside a hygen-island injection it
 * contributes nothing and the JS analyzer behaves exactly as before. This mirrors the
 * Angular (`Angular2TSReferencesContributor`) and Vue (`VueJSReferenceContributor`) pattern.
 */
class HygenEjsReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // Match every element shape; the provider itself decides (cheaply) whether the element is a
        // root reference expression inside a hygen-island injection. The language is already JS by
        // the time this contributor is invoked (plugin.xml `language="JavaScript"`).
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            HygenEjsReferenceProvider(),
        )
    }
}
