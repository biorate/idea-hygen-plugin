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
        assertEquals(setOf("name", "Name", "names", "Names", "ts", "_", "h"), context.variables)
        assertEquals(0, context.helpers.size)
    }

    // ---- engine per-run specials (params.ts: ts / _) ----

    fun testEngineSpecialsPresentAlongsideNames() {
        // The engine injects `ts` (HYGEN_TS/timestamp) and `_` (CLI positionals) into every run;
        // they must be visible in the default context. They do not collide with source names.
        val context = EngineDefaults.context
        assertTrue("ts" in context.variables)
        assertTrue("_" in context.variables)
        // The specials are distinct from the config/hook/prompt sources — a source of the same
        // name collapses rather than conflicting, set semantics keep a single element.
        assertTrue(EngineDefaults.names.all { it in context.variables })
        assertEquals(1, HygenTemplateContext.merge(
            ContextSources(configLocals = setOf("ts", "_")),
        ).variables.count { it == "ts" })
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

    /**
     * The real `application-template/.hygen.js` defines `helpers` as a FUNCTION
     * (`helpers: (locals, config) => ({ ... })`), not an object literal. Static extraction cannot
     * see inside the function body — helper names are resolved dynamically at runtime by the
     * context engine — so the parser must degrade gracefully: no throw, no names, and still no
     * `localsDefaults`.
     */
    fun testHelpersFromRealApplicationTemplateConfig() {
        val cfg = File("/www/biorate/application-template/.hygen.js")
        assumeTrue("@biorate application-template .hygen.js not found; skipping", cfg.isFile)
        val text = cfg.readText()
        assertTrue("real config must define a function-based helpers", "helpers:" in text)
        assertEquals(emptySet<String>(), JavaScriptConfigParser.helpersFromConfig(project, text))
        assertEquals(emptySet<String>(), JavaScriptConfigParser.localsFromConfig(project, text))
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

    // ---- spec: `.hygen.ts` read via the same JS PSI parser (task 1.3) ----

    fun testTsConfigLocalsAndHelpersRead() {
        // A `.hygen.ts` config is parsed with the platform's JS PSI (design.md D2): TS-flavoured
        // `module.exports` with a typed helper arrow is extracted exactly like a `.hygen.js`.
        val text = """
            |const path = require('path');
            |module.exports = {
            |  localsDefaults: { name: 'widget', scope: 'app' },
            |  helpers: { root: (p: string) => path.resolve(p) }
            |};
        """.trimMargin()
        assertEquals(setOf("name", "scope"), JavaScriptConfigParser.localsFromConfig(project, text))
        assertEquals(setOf("root"), JavaScriptConfigParser.helpersFromConfig(project, text))
    }

    fun testTsConfigExoticSyntaxDegradesGracefully() {
        // TS-only constructs the JS PSI cannot statically bind (`type` alias, `as`, `satisfies`,
        // `export default`) must yield empty sets, never throw — the graceful degradation that
        // keeps a non-parseable `.hygen.ts` a silent no-op.
        val text = """
            |type Config = { localsDefaults: { name: string } };
            |const cfg = { localsDefaults: { name: 'widget' } } satisfies Config;
            |export default cfg;
        """.trimMargin()
        assertEquals(emptySet<String>(), JavaScriptConfigParser.localsFromConfig(project, text))
        assertEquals(emptySet<String>(), JavaScriptConfigParser.helpersFromConfig(project, text))
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

    // ---- spec: config discovery follows the engine's resolution order (task 1.1 / 1.2) ----

    fun testConfigPriorityWithinDirectory() {
        // A single directory holding both `.hygen.js` and `.hygen.ts`: the engine reads the
        // `.hygen.js` (higher priority) and ignores `.hygen.ts`.
        val action = createTempDir()
        File(action, ".hygen.ts").writeText("module.exports = {}")
        File(action, ".hygen.js").writeText("module.exports = {}")
        assertEquals(File(action, ".hygen.js").path, HygenConfigLocator.findNearestConfig(action)?.path)
    }

    fun testEachConfigNameDiscoveredAlone() {
        // Each of the engine's four names is found when it is the only config present.
        for (name in HygenConfigLocator.CONFIG_FILE_NAMES) {
            val action = createTempDir()
            val cfg = File(action, name).apply { writeText("module.exports = {}") }
            assertEquals(cfg.path, HygenConfigLocator.findNearestConfig(action)?.path)
        }
    }

    fun testNearestDirectoryAlwaysWins() {
        // A nearer directory's `.hygen.ts` shadows a farther (root) `.hygen.js`: the engine never
        // looks past a nearer directory, so name priority only applies WITHIN a directory.
        val root = createTempDir()
        File(root, ".hygen.js").writeText("module.exports = {}")
        val gen = File(root, "_templates/gen").apply { mkdirs() }
        val action = File(gen, "action").apply { mkdirs() }
        File(gen, ".hygen.ts").writeText("module.exports = {}")
        assertEquals(File(gen, ".hygen.ts").path, HygenConfigLocator.findNearestConfig(action)?.path)
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
