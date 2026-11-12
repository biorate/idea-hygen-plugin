package com.biorate.hygen.context

import com.biorate.hygen.lang.HygenLanguage
import com.biorate.hygen.lang.psi.HygenFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assume.assumeTrue
import java.io.File

/**
 * Spec-driven tests for the template-context model (spec.md `hygen-template-context`, task 2.1).
 *
 * The [HygenTemplateContext] resolver is driven through its pure entry points — the per-source parse
 * functions in [JavaScriptConfigParser], the [HygenTemplateContext.merge] composition and the
 * positional [HygenTemplateContext.inTemplateDeclarations] — plus the filesystem [HygenConfigLocator],
 * so each source is tested in isolation and the merge/shadowing semantics are asserted directly.
 *
 * The JS config/hook parsing goes through the platform's JavaScript PSI (loaded as the "JavaScript"
 * plugin in the test IDE) without executing any code. Note the assertions use the two-argument
 * [org.junit.Assert.assertEquals] (JUnit 3) form, since [BasePlatformTestCase] extends
 * [junit.framework.TestCase].
 */
class HygenTemplateContextTest : BasePlatformTestCase() {

    // ---- engine defaults ----

    fun testEngineDefaultsAlwaysPresent() {
        val context = HygenTemplateContext.merge(ContextSources())
        assertEquals(setOf("name", "Name", "names", "Names", "h"), context.variables)
        assertEquals(0, context.helpers.size)
    }

    // ---- config: localsDefaults keys ----

    fun testLocalsDefaultsKeysOffered() {
        val text = "module.exports = { localsDefaults: { scope: 'app', owner: 'me' } };"
        assertEquals(setOf("scope", "owner"), JavaScriptConfigParser.localsFromConfig(project, text))
    }

    fun testConfigHelpersOffered() {
        val text = """
            |const minimist = require('minimist');
            |module.exports = {
            |  helpers: {
            |    root: (path) => path,
            |    stringify: (data) => JSON.stringify(data),
            |    _: lodash,
            |    get platform() { return 'linux'; }
            |  }
            |};
        """.trimMargin()
        // Getter (`platform`) and identifier-referenced helper (`_`) are both included by name.
        assertEquals(
            setOf("root", "stringify", "_", "platform"),
            JavaScriptConfigParser.helpersFromConfig(project, text),
        )
    }

    fun testHelpersFromRealApplicationTemplateConfig() {
        val cfg = File("/www/biorate/application-template/.hygen.js")
        assumeTrue("@biorate application-template .hygen.js not found; skipping", cfg.isFile)
        val helpers = JavaScriptConfigParser.helpersFromConfig(project, cfg.readText())
        assertEquals(
            setOf("_", "server", "client", "hygen", "stringify", "root", "defined", "platform"),
            helpers,
        )
        // No localsDefaults in the real config -> no locals.
        assertEquals(emptySet<String>(), JavaScriptConfigParser.localsFromConfig(project, cfg.readText()))
    }

    // ---- prompt hook: question names ----

    fun testQuestionNamesOffered() {
        val text = "module.exports = [{ name: 'module' }, { name: 'client', message: 'x' }];"
        assertEquals(setOf("module", "client"), JavaScriptConfigParser.hookVariables(project, text))
    }

    fun testQuestionNamesInExpressionModuleExports() {
        val text = "module.exports =;\nconst qs = [{ name: 'thing' }];\nmodule.exports = qs;"
        // Static object literals anywhere in the file contribute their string `name:` values.
        assertEquals(setOf("thing"), JavaScriptConfigParser.hookVariables(project, text))
    }

    // ---- prompt hook: params keys ----

    fun testParamsKeysOffered() {
        val text = """
            |module.exports = {
            |  params: ({ args, h }) => ({ locale: 'en', server: 'a' })
            |};
        """.trimMargin()
        assertEquals(setOf("locale", "server"), JavaScriptConfigParser.hookVariables(project, text))
    }

    fun testFunctionNamedParamsKeysOffered() {
        val text = "function params() { return { locale: 'en', server: 'a' }; }\nmodule.exports = params;"
        assertEquals(setOf("locale", "server"), JavaScriptConfigParser.hookVariables(project, text))
    }

    fun testParamsCallArgumentKeysOffered() {
        val text = "module.exports = params({ green: true, blue: false });"
        assertEquals(setOf("green", "blue"), JavaScriptConfigParser.hookVariables(project, text))
    }

    // ---- prompt hook: dynamic names skipped ----

    fun testDynamicQuestionNameSkipped() {
        val text = "const answer = 'q';\nmodule.exports = [{ name: answer }, { name: computed() }];"
        assertEquals(emptySet<String>(), JavaScriptConfigParser.hookVariables(project, text))
    }

    // ---- missing / unparsable config degrades gracefully ----

    fun testMissingOrEmptyConfigYieldsNothing() {
        assertEquals(emptySet<String>(), JavaScriptConfigParser.localsFromConfig(project, ""))
        assertEquals(emptySet<String>(), JavaScriptConfigParser.helpersFromConfig(project, ""))
        assertEquals(emptySet<String>(), JavaScriptConfigParser.hookVariables(project, ""))
    }

    fun testUnparsableConfigYieldsNothing() {
        val text = "this is not javascript ;;; { { {"
        assertEquals(emptySet<String>(), JavaScriptConfigParser.localsFromConfig(project, text))
        assertEquals(emptySet<String>(), JavaScriptConfigParser.helpersFromConfig(project, text))
        assertEquals(emptySet<String>(), JavaScriptConfigParser.hookVariables(project, text))
    }

    fun testConfigWithoutExportsYieldsNothing() {
        assertEquals(emptySet<String>(), JavaScriptConfigParser.localsFromConfig(project, "const x = 1;"))
    }

    // ---- in-template declarations (positional) ----

    fun testDeclarationVisibleBelowItsIsland() {
        val (file, offset) = templateWithMarker(
            "---\nto: app/x.js\n---\n<% APP_NAME = 'demo' %>\n<%= APP_NAME %>\n",
            "<%= APP_NAME %>",
        )
        val declared = HygenTemplateContext.inTemplateDeclarations(file, offset)
        assertEquals(setOf("APP_NAME"), declared)
    }

    fun testConstDeclarationVisibleBelowIsland() {
        val (file, offset) = templateWithMarker(
            "---\nto: app/x.js\n---\n<% const upper = Name %>\n<%= upper %>\n",
            "<%= upper %>",
        )
        assertEquals(setOf("upper"), HygenTemplateContext.inTemplateDeclarations(file, offset))
    }

    fun testNoBackPropagationAboveIsland() {
        // Offset BEFORE the declaration island (at the reference island above it) sees nothing.
        val (file, offset) = templateWithMarker(
            "---\nto: app/x.js\n---\n<%= APP_NAME %>\n<% APP_NAME = 'demo' %>\n",
            "<%= APP_NAME %>",
        )
        assertEquals(emptySet<String>(), HygenTemplateContext.inTemplateDeclarations(file, offset))
    }

    fun testExactOffsetAtIslandEndIncludesIt() {
        // island.endOffset <= offset: an offset right at the declaration island's end includes it.
        val text = "---\nto: app/x.js\n---\n<% MODE = 'x' %>\n"
        val file = hogen(text)
        val islandEnd = text.indexOf("<% MODE = 'x' %>") + "<% MODE = 'x' %>".length
        assertEquals(setOf("MODE"), HygenTemplateContext.inTemplateDeclarations(file, islandEnd))
    }

    fun testArrowFunctionIsNotADeclaration() {
        // `arg => value` (an arrow) must not be treated as a bare `X = ...` assignment.
        val (file, offset) = templateWithMarker("---\nto: x\n---\n<% fn = (arg) => arg %>\n<%= fn %>\n", "<%= fn %>")
        assertEquals(setOf("fn"), HygenTemplateContext.inTemplateDeclarations(file, offset))
    }

    // ---- merged context: shadowing ----

    fun testShadowingCollapsesToSingleName() {
        // Same name from config and template — offered once in the completed set.
        val context = HygenTemplateContext.merge(
            ContextSources(
                configLocals = setOf("name", "scope"),
                configHelpers = setOf("root"),
                hookVariables = setOf("module"),
                templateDeclarations = setOf("name", "module"),
            ),
        )
        assertEquals(1, context.variables.count { it == "name" })
        assertEquals(1, context.variables.count { it == "module" })
        assertTrue("scope" in context.variables)
        assertTrue("name" in context.variables)
        assertEquals(setOf("root"), context.helpers)
    }

    fun testLaterSourcesAddToEngineDefaults() {
        val context = HygenTemplateContext.merge(
            ContextSources(configLocals = setOf("extra"), configHelpers = setOf("hx")),
        )
        assertTrue("extra" in context.variables)
        assertTrue(setOf("name", "Name", "names", "Names", "h").all { it in context.variables })
        assertTrue("hx" in context.helpers)
    }

    // ---- filesystem resolution ----

    fun testNearestConfigWalkFindsNearest() {
        val root = createTempDir()
        File(root, ".hygen.js").writeText("module.exports = {}")
        val gen = File(root, "_templates/gen").apply { mkdirs() }
        val action = File(gen, "action").apply { mkdirs() }
        assertEquals(File(root, ".hygen.js").path, HygenConfigLocator.findNearestConfig(action)?.path)
        // A nearer config shadows the root one.
        File(gen, ".hygen.js").writeText("module.exports = {}")
        assertEquals(File(gen, ".hygen.js").path, HygenConfigLocator.findNearestConfig(action)?.path)
    }

    fun testNoConfigAboveReturnsNull() {
        val action = createTempDir()
        assertNull(HygenConfigLocator.findNearestConfig(File(action, "action")))
    }

    fun testHookFirstExistingOrder() {
        val action = createTempDir()
        assertNull(HygenConfigLocator.findHook(action))
        // prompt.ts wins over index.js when both exist (engine name order).
        File(action, "index.js").writeText("")
        assertEquals(File(action, "index.js").path, HygenConfigLocator.findHook(action)?.path)
        File(action, "prompt.ts").writeText("")
        assertEquals(File(action, "prompt.ts").path, HygenConfigLocator.findHook(action)?.path)
    }

    private fun createTempDir(): File =
        com.intellij.openapi.util.io.FileUtil.createTempDirectory("hygen-ctx-test", "")

    // ---- config cache invalidation (modification stamp) ----

    /**
     * Spec (synthetic declarations): the prelude is regenerated when `.hygen.js` changes. The
     * context cache is keyed on the config path + modification stamp, so a changed `.hygen.js`
     * is re-parsed on the next [HygenTemplateContext.visibleAt] and the new helpers appear.
     */
    fun testConfigChangeInvalidatesCache() {
        val root = createTempDir()
        val config = File(root, ".hygen.js")
        config.writeText("module.exports = { helpers: { root: (p) => p } };")

        val action = File(root, "_templates/gen/action").apply { mkdirs() }
        val templatePath = File(action, "my.ejs.t").apply {
            writeText("---\nto: app/x.js\n---\n<%= root('x') %>\n")
        }.absolutePath

        // Load the template through the real VFS so `actionDir` resolves its parent on disk.
        val vf = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(templatePath)!!
        val file = com.intellij.psi.PsiManager.getInstance(project).findFile(vf) as HygenFile

        val before = HygenTemplateContext.visibleAt(file, file.text.indexOf("<%"))
        assertTrue("root helper should be visible", "root" in before.helpers)

        // Change the config; the next resolution must re-parse (cache invalidated by stamp).
        config.writeText("module.exports = { helpers: { root: (p) => p, extra: (p) => p } };")
        val after = HygenTemplateContext.visibleAt(file, file.text.indexOf("<%"))
        assertTrue("new helper 'extra' should be visible after config change", "extra" in after.helpers)
        assertTrue("root should still be visible", "root" in after.helpers)
    }

    // ---- helpers ----

    private fun hogen(text: String): HygenFile =
        PsiFileFactory.getInstance(project)
            .createFileFromText("x.ejs.t", HygenLanguage, text) as HygenFile

    /** Parses [text] and returns (file, offset) where [marker] first occurs. */
    private fun templateWithMarker(text: String, marker: String): Pair<HygenFile, Int> {
        val offset = text.indexOf(marker)
        require(offset >= 0) { "marker not found in fixture" }
        return hogen(text) to offset
    }
}
