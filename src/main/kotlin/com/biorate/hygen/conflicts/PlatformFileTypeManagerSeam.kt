package com.biorate.hygen.conflicts

import com.biorate.hygen.lang.HygenFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileTypes.FileTypeManager
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory

/**
 * Production [FileTypeProbe] and [ShadowingPatternRemover] over the platform
 * [FileTypeManager] (design D1/D2). Every call is guarded: a missing manager or a failing lookup
 * is a "no result"/"no removal" outcome, never a crash — detection and resolution must not break
 * the association sync that hosts them.
 */
class PlatformFileTypeManagerSeam internal constructor(
    private val fileTypes: FileTypeManager,
) : FileTypeProbe, ForeignPatterns, ShadowingPatternRemover {

    override fun getFileTypeByFileName(fileName: String): String? = runCatching {
        fileTypes.getFileTypeByFileName(fileName).name
    }.getOrNull()

    override fun foreignTypes(): List<ForeignFileType> {
        // Temporary diagnostics (debug build): read access may be required for
        // registeredFileTypes/getAssociations in some contexts.
        val result = runCatching {
            ApplicationManager.getApplication()?.runReadAction<List<ForeignFileType>> {
                fileTypes.registeredFileTypes
                    .filter { it.name != HygenFileType.name }
                    .map { type ->
                        ForeignFileType(
                            name = type.name,
                            patterns = runCatching {
                                fileTypes.getAssociations(type).map { it.presentableString }
                            }.getOrDefault(emptyList()),
                        )
                    }
                    .filter { it.patterns.isNotEmpty() }
            } ?: emptyList()
        }
        return result.fold(
            onSuccess = { types ->
                LOG.run {
                    val envType = types.firstOrNull { it.name == ".env file" }
                    when {
                        envType == null -> warn(
                            "Hygen diagnostics: foreignTypes(): ${types.size} foreign types, " +
                                "'.env file' NOT present",
                        )
                        envType.patterns.isEmpty() -> warn(
                            "Hygen diagnostics: foreignTypes(): ${types.size} foreign types, " +
                                "'.env file' present but with EMPTY patterns",
                        )
                        else -> info(
                            "Hygen diagnostics: foreignTypes(): ${types.size} foreign types, " +
                                "'.env file' present with patterns ${envType.patterns}",
                        )
                    }
                }
                types
            },
            onFailure = { throwable ->
                LOG.warn("Hygen diagnostics: foreignTypes() enumeration failed", throwable)
                emptyList()
            },
        )
    }

    override fun removePattern(shadowingFileTypeName: String, pattern: String): Boolean {
        // The write action must run outside runCatching's non-local-return scope.
        val application = ApplicationManager.getApplication() ?: return false
        val shadowing = runCatching { fileTypes.findFileTypeByName(shadowingFileTypeName) }.getOrNull()
            ?: return false
        return runCatching {
            application.runWriteAction {
                fileTypes.removeAssociation(
                    shadowing,
                    FileNameMatcherFactory.getInstance().createMatcher(pattern),
                )
            }
            true
        }.getOrDefault(false)
    }

    companion object {
        private val LOG = Logger.getInstance(PlatformFileTypeManagerSeam::class.java)

        /**
         * @return the seam backed by the current [FileTypeManager], or `null` when the manager is
         *         not yet available (early startup, bare test context) so the caller can skip.
         */
        fun fromFileTypeManager(): PlatformFileTypeManagerSeam? = runCatching {
            PlatformFileTypeManagerSeam(FileTypeManager.getInstance())
        }.getOrNull()
    }
}
