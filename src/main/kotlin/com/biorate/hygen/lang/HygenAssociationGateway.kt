package com.biorate.hygen.lang

import com.intellij.openapi.fileTypes.FileTypeManager
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory

/**
 * Seam over the platform [FileTypeManager] association calls so the diff in [HygenPatternAssociator]
 * can be unit-tested without the IDE (design D5). Production code uses
 * [FileTypeManagerAssociationGateway]; tests substitute a recording fake that verifies the exact
 * associate/remove sequences.
 */
interface HygenAssociationGateway {

    /** Patterns currently associated with [HygenFileType] (wildcards like `*.t`, plus any exact-name associations from the claim flow). */
    fun currentPatterns(): Set<String>

    /** Associates [pattern] (wildcard form) with [HygenFileType]. */
    fun associate(pattern: String)

    /** Removes the association for [pattern] (wildcard form) from [HygenFileType]. */
    fun remove(pattern: String)
}

/**
 * Production [HygenAssociationGateway] delegating to the platform [FileTypeManager]. The plugin
 * only ever creates wildcard matchers ([*...]), so [currentPatterns] reads the presentable wildcard
 * string back from each [com.intellij.openapi.fileTypes.FileNameMatcher].
 */
class FileTypeManagerAssociationGateway internal constructor(
    private val fileTypeManager: FileTypeManager,
) : HygenAssociationGateway {

    override fun currentPatterns(): Set<String> =
        fileTypeManager.getAssociations(HygenFileType).mapTo(LinkedHashSet()) { it.presentableString }

    override fun associate(pattern: String) =
        fileTypeManager.associatePattern(HygenFileType, pattern)

    override fun remove(pattern: String) =
        fileTypeManager.removeAssociation(
            HygenFileType,
            FileNameMatcherFactory.getInstance().createMatcher(pattern),
        )

    companion object {
        /**
         * @return the gateway backed by the current [FileTypeManager], or `null` when the manager
         *         is not yet available (e.g. during early startup or in a bare test context) so
         *         the caller can skip safely instead of crashing.
         */
        fun fromFileTypeManager(): HygenAssociationGateway? = runCatching {
            FileTypeManagerAssociationGateway(FileTypeManager.getInstance())
        }.getOrNull()
    }
}
