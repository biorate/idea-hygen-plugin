# Verification — idea-hygen-plugin MVP

Verification run: **2026-11-12** · IDE platform source **IDEA IU-253.30387.90** (local install)
Command: `./gradlew cleanTest test --console=plain`

## 1. Automated test results

| Test class | Tests | Failures | Errors | Skipped | Notes |
|---|---|---|---|---|---|
| `HygenCatalogIntegrityTest` | 17 | 0 | 0 | 0 | `catalog` — integrity of frontmatter/changeCase/inflection catalogs |
| `HygenCompletionTest` | 16 | 0 | 0 | 0 | `completion` — `computeProposals` (production path) |
| `HygenEjsInspectionTest` | 11 | 0 | 0 | 0 | `validation` — unterminated tag, `<%%` escape, hook-file EJS |
| `HygenFrontmatterInspectionTest` | 13 | 0 | 0 | 0 | `validation` — unknown key, inject+replace, anchor w/o inject, broken delimiter |
| `HygenRealWorldTemplatesTest` | 1 | 0 | 0 | **0** | Ran (not skipped) against the 29 real templates — **zero problems** |
| `HygenHighlightingTest` | 4 | 0 | 0 | 0 | `language` — token→attribute-key wiring, color settings page |
| `HygenFileTypeOverriderTest` | 3 | 0 | 0 | 0 | `host-detection` — hook files vs templates classification |
| `HygenHostDetectionTest` | 10 | 0 | 0 | 0 | `host-detection` — `detectHost` per scenario |
| `HygenPsiTest` | 5 | 0 | 0 | 0 | `language` — file-type registration, PSI tree shape, `toValue` |
| `HygenTokenizerTest` | 6 | 0 | 0 | 0 | `language` — lexer: frontmatter/body/EJS families + trimming |
| `HygenInjectionTest` | 3 | 0 | 0 | 0 | `host-detection` — injector contracts (host language + ranges) |
| **Total** | **89** | **0** | **0** | **0** | **BUILD SUCCESSFUL** |

**Real-world templates (task 5.3):** `HygenRealWorldTemplatesTest.testRealWorldTemplatesHaveZeroProblems` was **not skipped** (skipped=0). It read **29** `.ejs.t` files straight off disk from `/www/biorate/core/packages/@biorate/hygen` (6 in `src/templates/init/**`, 23 valid fixtures in `tests/fixtures/**`, excluding the deliberately-broken `inject-error` subtree) and ran the full file-level inspection suite — zero warnings, zero errors. No false positives on real templates.

---

## 2. Scenario coverage map

Legend: **✅** automated tests pass · **⚠️** logic automated, *editor-visible* half is manual · **🖐** manual only.

### `hygen-template-language`

| Scenario | Covered by | Status |
|---|---|---|
| Extension-based recognition (`.ejs.t`) | `HygenPsiTest.testFileTypeIsRegisteredForEjsTemplates` | ✅ (⚠️ opening a real project file on disk = manual) |
| Plain-text fallback is not required | `HygenTokenizerTest.\`file without frontmatter is body only\``, `HygenPsiTest.testBodyOnlyFile` | ✅ |
| Expression tag (`<%= %>`) | `HygenTokenizerTest.\`supports all EJS tag families\``, `HygenHighlightingTest.testTokenToAttributeKeyMapping` | ⚠️ colors rendered in editor = manual |
| Scriptlet and comment tags (`<% %>`/`<%# %>`) | `HygenTokenizerTest.\`supports all EJS tag families\``, `HygenHighlightingTest` | ⚠️ |
| Broken tag is still delimited | `HygenTokenizerTest.\`reports unterminated tag\``, `HygenPsiTest.testUnterminatedTagProducesNode`, `HygenHighlightingTest` | ⚠️ |
| Frontmatter delimiters | `HygenTokenizerTest.\`tokenizes frontmatter and body\``, `HygenHighlightingTest.testTokenToAttributeKeyMapping` | ⚠️ |
| Both layers are EJS-renderable | `HygenTokenizerTest.\`tokenizes frontmatter and body\`` (EJS tokens inside `to:` value) | ⚠️ |

### `hygen-host-detection`

| Scenario | Covered by | Status |
|---|---|---|
| Static target extension (`report.js`) | `HygenHostDetectionTest.testStaticTargetExtension` | ✅ |
| Interpolated path segments (`app/<%= x %>/<%= n %>.ts`) | `HygenHostDetectionTest.testInterpolatedPathSegments` | ✅ |
| Fully dynamic target (fallback to filename) | `HygenHostDetectionTest.testFullyDynamicTargetFallsBackToFileName` | ✅ |
| Conventional suffixed filename (`hello.js.ejs.t`) | `HygenHostDetectionTest.testConventionalSuffixedFileName` | ✅ |
| Unconventional filename (`hello.ejs.t`, no `to:`) | `HygenHostDetectionTest.testUnconventionalFileNameIsPlainText` | ✅ |
| Hook files are not templates (`prompt.js`/`index.js`/`prompt.ts`/`index.ts`) | `HygenFileTypeOverriderTest.testHookFilesAreNotClaimedAsTemplates`; hook-file EJS warning: `HygenEjsInspectionTest.testEjsInPromptJsWarns` / `testEjsInIndexTsWarns` | ✅ (⚠️ hook file *highlighted as plain JS on disk* = manual) |
| Unsupported extension (`report.xyzzy` → plain text) | `HygenHostDetectionTest.testUnsupportedExtensionDegradesToPlainText` | ✅ |
| *Injection visual — body rendered with host-language colors* | `HygenInjectionTest` (registrar contract only: languageId + injected ranges) | 🖐 **manual** |

> `HygenInjectionTest` proves the injector declares the right host language and text ranges, but it does not materialize an injected *document in the real editor*. Whether the body **visually** shows host-language highlighting after injection is a sandbox check.

### `hygen-validation`

| Scenario | Covered by | Status |
|---|---|---|
| Fork-specific keys accepted (`create_if_missing`, `replace`) | `HygenFrontmatterInspectionTest.testForkSpecificKeysAreNotUnknown` | ✅ |
| Unknown key warns, names it, doesn't suppress others | `HygenFrontmatterInspectionTest.testUnknownKeyWarnsWithName`, `testUnknownKeyDoesNotSuppressOtherInspection` | ✅ |
| `inject` together with `replace` → error | `HygenFrontmatterInspectionTest.testInjectWithReplaceConflicts` | ✅ |
| Anchor option without `inject: true` → warning | `HygenFrontmatterInspectionTest.testBeforeWithoutInjectWarns`, `testEveryAnchorOptionWithoutInjectWarns`, `testAnchorWithInjectIsNotWarned` | ✅ |
| Unterminated EJS tag → error | `HygenEjsInspectionTest.testUnterminatedTagErrors`, `testUnterminatedTagInFrontmatterValueErrors`, `testClosedTagFollowedByUnterminatedErrorsOnce` | ✅ |
| Escaped literal `<%%` → no error | `HygenEjsInspectionTest.testEscapedLiteralIsNotError` | ✅ |
| EJS syntax in hook file (`prompt.js`) → warning | `HygenEjsInspectionTest.testEjsInPromptJsWarns`, `testEjsInIndexTsWarns` | ✅ |
| Missing closing frontmatter delimiter → error | `HygenFrontmatterInspectionTest.testUnterminatedFrontmatterErrors` | ✅ |

### `hygen-completion`

| Scenario | Covered by | Status |
|---|---|---|
| Key completion in frontmatter (with descriptions) | `HygenCompletionTest.testEmptyFrontmatterLineOffersAllKeys`, `testFrontmatterKeysCarryDescriptions`, `testPartiallyTypedKeyIsOffered` | ⚠️ popup UX = manual |
| Fork-only keys offered | same `testEmptyFrontmatterLineOffersAllKeys` (asserts full `knownKeys` incl. fork keys) | ⚠️ |
| Helper namespace completion (`h.` → changeCase/inflection/capitalize/path) | `HygenCompletionTest.testHDotOffersTopLevelHelpers`, `testBareHOffersTopLevelHelpers` | ⚠️ |
| Nested helper member completion (`h.changeCase.` / `h.inflection.`) | `HygenCompletionTest.testChangeCaseDotOffersAllMembers`, `testInflectionDotOffersAllMembers`, `testChangeCasePartialMemberIsOffered` | ⚠️ |
| Completion degrades on broken template | `HygenCompletionTest.testUnterminatedTagStillOffersHelperCompletion`, `testUnterminatedChangeCaseTagStillOffersMembers` | ⚠️ |

> `HygenCompletionTest` drives the production `HygenCompletion.computeProposals` function, so candidate *content* is verified. Whether the **popup actually appears and inserts** on Ctrl+Space in the real editor is not covered by the platform test fixture (BasePlatformTestCase does not render the completion popup).

---

## 3. Uncovered-by-automated-test scenarios (need the human GUI pass)

Every spec scenario is covered by **at least one** automated test at the logic level. **No scenario is fully uncovered.** The gaps are all the *editor-visible* half of otherwise-tested behaviors:

- **Injection visual** — body text should be rendered/highlighted as the detected host language in the editor.
- **Highlighting colors** — the configured EJS/frontmatter colors actually appear as configured (not overridden by defaults).
- **Completion popup behavior** — Ctrl+Space shows the popup with the computed candidates; typing/selecting inserts correctly. Applies to all 5 completion scenarios.
- **Real-project file-type routing** — opening a real `_templates/**` file on disk takes the hygen language path (fixtures use in-memory files).

---

## 4. Manual check checklist (run in the sandbox IDEA 2025.3)

Open the plugin project and the real template dir (`/www/biorate/core/packages/@biorate/hygen`). Reference files are listed below.

### 4.1 File-type routing & highlighting

| File to open | Expected |
|---|---|
| `src/templates/init/self/new-generator.ejs.t` | Opens as a **Hygen Template** (Language: Hygen Template in status bar) — frontmatter block distinct, EJS tags colored. |
| `src/templates/init/self/new-generator-with-prompt.prompt.ejs.t` | Frontmatter + body; EJS tags highlighted in EJS colors, frontmatter keys/values in frontmatter colors. |
| `tests/fixtures/kitchen-sink/_templates/sink/new/03-inject.ejs.t` | Fully highlighted — frontmatter delimiters/keys/values + `<% %>`, `<%= %>`, `<%# %>`, `<%%` escape each with their own color. |

### 4.2 Hook files are plain JS

| File to open | Expected |
|---|---|
| `tests/fixtures/app/action-inject/..` (or a `prompt.js`) — e.g. create `_templates/mygen/new/prompt.js` | Opens as **plain JavaScript**: red squiggles on JS syntax, **no** frontmatter/EJS highlighting, template inspections do **not** run. A pasted `<%= name %>` yields the **"EJS tags are not processed in hook files"** warning. |

### 4.3 Host-language injection visual

| File to open | Expected |
|---|---|
| A template with `to: app/x.js` and JS body (e.g. any `src/templates/**` that targets `.js`) | The plain **body text is highlighted as JavaScript** (keywords, strings, etc.), but the `<%= %>` tags are **not** part of the injected region. A `to:` value with no extension → body stays plain text. |

### 4.4 Inspections (Level-1, on a scratch `.ejs.t` pasted into a `_templates` folder)

| Input | Expected |
|---|---|
| `to: app/x.js` + `create_if_missing: true` + `replace: true` | No "unknown key" on the fork keys. |
| `too: app/x.js` (typo) | **Warning** "Unknown frontmatter key 'too'". |
| `inject: true` + `replace: true` | **Error** on both conflicting keys. |
| `before: "// a"` with no `inject: true` | **Warning** anchor ignored in add mode. |
| Body `<%= name` (no `%>`) | **Error** "Unterminated EJS tag". |
| Body `<%%= name %>` | **No** error (literal escape). |
| Frontmatter opened with `---` and never closed | **Error** "Unterminated frontmatter". |

### 4.5 Completion popup

| Input | Expected |
|---|---|
| Empty frontmatter line, press **Ctrl+Space** | Popup lists the known frontmatter keys (`to`, `inject`, `sh`, `before`, `after`, `prepend`, `append`, `at_line`, `skip_if`, `skip_if_not`, `create_if_missing`, `replace`, `unless_exists`, `local`) each with a one-line description. |
| Type `h.` inside `<%= %>`, press **Ctrl+Space** | Popup offers `changeCase`, `inflection`, `capitalize`, `path`. |
| Type `h.changeCase.`, press **Ctrl+Space** | Popup lists change-case 3.x functions (e.g. `pascalCase`). `h.inflection.` → inflection 1.x (e.g. `pluralize`). |
| Body with an unterminated `<%`, then `h.` + Ctrl+Space | Completion still offered, no error dialog. |

### 4.6 Note

The plugin is built and installed into the local IDEA 2025.3. If a check above fails, record the observed behavior and return it; the failing scenario needs a fix before the change is archived. Task 7.2 (`./gradlew buildPlugin` + install) is the separate build/install step that must already have been performed for this sandbox pass.

---

# Verification — change `ejs-context-and-injection` (0.2.x)

Verification run: **2026-11-12** · Command: `./gradlew cleanTest test --console=plain`
This is a **delta section** appended by task 4.1. The MVP sections above remain authoritative for their own change; everything here covers the `ejs-context-and-injection` change (template-context + EJS-island injection + synthetic declarations).

## A. Full-suite results — 165 tests, 0 failures / 0 errors / 0 skipped

| Test class | Tests | Failures | Errors | Skipped | Notes |
|---|---|---|---|---|---|
| `HygenCatalogIntegrityTest` | 17 | 0 | 0 | 0 | `catalog` — integrity of frontmatter/changeCase/inflection catalogs |
| `HygenCompletionTest` | 26 | 0 | 0 | 0 | `completion` — frontmatter/helper/context-variable proposals |
| `HygenTemplateContextRealTemplatesTest` | 1 | 0 | 0 | 0 | `context` — **NEW (4.1)** `visibleAt` corpus sweep (never throws, engine defaults) |
| `HygenTemplateContextTest` | 24 | 0 | 0 | 0 | `context` — defaults/`.hygen.js`/prompt-hook/in-template/merge/shadowing/cache |
| `HygenEjsInspectionTest` | 11 | 0 | 0 | 0 | `validation` — unterminated tag, `<%%` escape, hook-file EJS |
| `HygenFrontmatterInspectionTest` | 13 | 0 | 0 | 0 | `validation` — unknown key, inject+replace, anchor w/o inject, broken delimiter |
| `HygenRealWorldTemplatesTest` | 1 | 0 | 0 | **0** | Ran (not skipped) — **zero problems** on 29 real `@biorate/hygen` templates |
| `HygenFileTypeOverriderTest` | 6 | 0 | 0 | 0 | `host-detection` — hook files vs templates classification |
| `HygenHostDetectionTest` | 10 | 0 | 0 | 0 | `host-detection` — `detectHost` per scenario |
| `HygenLexerContinuityTest` | 4 | 0 | 0 | 0 | `language` — lexer continuity (2 fixtures + 2 real-corpus), editor-flash regression |
| `HygenPsiTest` | 5 | 0 | 0 | 0 | `language` — file-type registration, PSI tree shape, `toValue` |
| `HygenTokenizerTest` | 6 | 0 | 0 | 0 | `language` — lexer: frontmatter/body/EJS families + trimming |
| `HygenHighlightingTest` | 4 | 0 | 0 | 0 | `language` — token→attribute-key wiring, color settings page |
| `HygenEjsContextualNamesTest` | 9 | 0 | 0 | 0 | `injection` — `resolveRoot` / `isGroupMember` for synthetic declarations |
| `HygenEjsIslandInjectorTest` | 11 | 0 | 0 | 0 | `injection` — island JS injection incl. `testCorpusBodyAndIslandRangesNeverOverlap` |
| `HygenEjsPlaceholderInjectorTest` | 2 | 0 | 0 | 0 | `injection` — `MultiHostRegistrarPlaceholderHelper` reflective adapter |
| `HygenEjsPreludeTest` | 9 | 0 | 0 | 0 | `injection` — synthetic prelude (`h` skeleton, catalogs, helpers, engine defaults) |
| `HygenEjsReferenceProviderTest` | 3 | 0 | 0 | 0 | `injection` — reference contributor safety contract |
| `HygenInjectionTest` | 3 | 0 | 0 | 0 | `host-detection` — body injector contracts (host language + ranges) |
| **Total** | **165** | **0** | **0** | **0** | **BUILD SUCCESSFUL** |

## B. Scenario → test mapping — `hygen-template-context`

| Scenario | Covered by | Status |
|---|---|---|
| Bare template without config (engine defaults `name`/`Name`/`names`/`Names`/`h`) | `HygenTemplateContextTest.testEngineDefaultsAlwaysPresent`, `testLaterSourcesAddToEngineDefaults`; `HygenCompletionTest.testDefaultsOnlyFileOffersEngineNameVariants`, `testPartialIdentifierOffersNameVariants`, `testUnterminatedTagStillOffersContextVariables`; `HygenTemplateContextRealTemplatesTest.testVisibleAtNeverThrowsAndAlwaysHasEngineDefaults`; `HygenEjsPreludeTest.testPreludeDeclaresEngineDefaultsEvenWithoutContext` | ✅ (⚠️ completion popup / editor = manual) |
| `localsDefaults` keys offered | `HygenTemplateContextTest.testLocalsDefaultsKeysOffered`; `HygenCompletionTest.testConfigLocalsBackedFileOffersLocals`; `HygenEjsContextualNamesTest.testLocalsAndHookVariablesResolve` | ✅ (⚠️ popup = manual) |
| Project helpers merge into `h` | `HygenTemplateContextTest.testConfigHelpersOffered`, `testHelpersFromRealApplicationTemplateConfig`; `HygenCompletionTest.testConfigHelpersMergeIntoHDotCompletion`, `testConfigHelpersCarryDescriptions`; `HygenEjsContextualNamesTest.testConfigHelperResolvesBare`, `testHMemberAccessResolves`; `HygenEjsPreludeTest.testPreludeDeclaresConfigHelpersCallableOrAny` | ✅ (⚠️ no false errors in editor = manual) |
| Missing / unparsable config degrades | `HygenTemplateContextTest.testMissingOrEmptyConfigYieldsNothing`, `testUnparsableConfigYieldsNothing`, `testConfigWithoutExportsYieldsNothing`; `HygenCompletionTest.testMissingConfigFallsBackToEngineDefaults` | ✅ |
| Question names offered | `HygenTemplateContextTest.testQuestionNamesOffered`, `testQuestionNamesInExpressionModuleExports`; `HygenCompletionTest.testConfigLocalsBackedFileOffersLocals` | ✅ |
| `params` keys offered | `HygenTemplateContextTest.testParamsKeysOffered`, `testFunctionNamedParamsKeysOffered`, `testParamsCallArgumentKeysOffered` | ✅ |
| Dynamic names skipped | `HygenTemplateContextTest.testDynamicQuestionNameSkipped` | ✅ |
| In-template declaration visible below its island | `HygenTemplateContextTest.testDeclarationVisibleBelowItsIsland`, `testConstDeclarationVisibleBelowIsland`, `testExactOffsetAtIslandEndIncludesIt`, `testArrowFunctionIsNotADeclaration`; `HygenCompletionTest.testDeclarationBelowCaretIsOffered`, `testConstDeclarationBelowCaretIsOffered`; `HygenEjsContextualNamesTest.testInTemplateDeclarationResolves` | ✅ |
| No back-propagation | `HygenTemplateContextTest.testNoBackPropagationAboveIsland`; `HygenCompletionTest.testNoBackPropagationAboveCaret` | ✅ |
| Merged context w/o conflicts (shadowing) | `HygenTemplateContextTest.testShadowingCollapsesToSingleName`, `testLaterSourcesAddToEngineDefaults` | ✅ |
| Nearest-`.hygen.js` walk-up + hook order | `HygenTemplateContextTest.testNearestConfigWalkFindsNearest`, `testNoConfigAboveReturnsNull`, `testHookFirstExistingOrder`; `testConfigChangeInvalidatesCache` (VFS cache invalidation) | ✅ |

## C. Scenario → test mapping — `hygen-ejs-injection`

| Scenario | Covered by | Status |
|---|---|---|
| Expression gets JS features (`<%= … %>`, `<% … %>`, trim variants) | `HygenEjsIslandInjectorTest.testExpressionGetsJsInjected`, `testScriptletGetsJsInjected`, `testTrimMarkersExcluded`, `testMinusTrimMarkersExcluded`, `testIslandCodeRangeTrimsWhitespace` | ✅ (⚠️ colors in real editor = manual) |
| Non-code regions untouched (frontmatter/body/escape/comment/empty) | `HygenEjsIslandInjectorTest.testEscapeHasNoRange`, `testCommentIslandHasNoRange`, `testEmptyIslandHasNoRange`, `testWhitespaceOnlyIslandHasNoRange`; body side `HygenInjectionTest`; corpus `testCorpusBodyAndIslandRangesNeverOverlap` | ✅ (⚠️ visual = manual) |
| `h.changeCase` chain resolves | `HygenEjsPreludeTest.testPreludeDeclaresHNamespace`, `testPreludeDeclaresAllChangeCaseMembers`, `testPreludeParsesAsJavaScript`; `HygenEjsContextualNamesTest.testHNamespaceAlwaysResolves`, `testHMemberAccessResolves`, `testGroupMembersAreKnown` | ✅ (⚠️ injected-editor resolution = manual) |
| Project helper resolves | `HygenEjsPreludeTest.testPreludeDeclaresConfigHelpersCallableOrAny`; `HygenEjsContextualNamesTest.testConfigHelperResolvesBare`, `testHMemberAccessResolves` | ✅ (⚠️ no false errors = manual) |
| Graceful degradation (unterminated / empty / unparsable) | `HygenEjsIslandInjectorTest.testUnterminatedTagHasNoRange`, `testEmptyIslandHasNoRange`, `testEscapeHasNoRange`; `HygenEjsReferenceProviderTest.testProviderReturnsEmptyForNonInjectedElement`, `testProviderReturnsEmptyForBodyLeaf`, `testReferenceContributorRegistersWithoutThrowing`; `HygenEjsPlaceholderInjectorTest.testCreateWrapsRegistrarWhenClassPresent`, `testInjectReachesRegistrarAndCompletes`; `HygenCompletionTest.testUnterminatedTagStillOffersContextVariables` | ✅ (⚠️ "no plugin exception logged" = manual) |

## D. Corpus sweep (tasks 3.1 / 4.1) — RAN, not skipped

All three sweeps read real template files off disk and were **confirmed RUNNING** (`skipped = 0`; each guards on directory presence with `assumeTrue`, and the corpus is present on this machine):

1. **Injection-overlap sweep** — `HygenEjsIslandInjectorTest.testCorpusBodyAndIslandRangesNeverOverlap` over `/www/biorate/application-template/_templates` (**186** `.ejs.t` files): asserts the body-injector ranges and the EJS-island injector ranges **never overlap** (a host/range leak would fail the file). ✅ 0 failures.
2. **Inspection zero-false-positive sweep** — `HygenRealWorldTemplatesTest.testRealWorldTemplatesHaveZeroProblems` over `/www/biorate/core/packages/@biorate/hygen` (**29** files: 6 `src/templates/**` + 23 `tests/fixtures/**`, excluding the deliberately-broken `inject-error` subtree): the full file-level inspection suite reports **zero** warnings/errors. ✅
3. **Context-resolution sanity sweep (NEW)** — `HygenTemplateContextRealTemplatesTest.testVisibleAtNeverThrowsAndAlwaysHasEngineDefaults` over a sample (12) of `/www/biorate/application-template/_templates` at every EJS island offset: `HygenTemplateContext.visibleAt` **never throws** and always returns a context containing the engine defaults (`name`/`Name`/`names`/`Names`/`h`). ⚠️ In-memory harness (an in-memory file has no `VirtualFile`, so the on-disk config/hook path degrades to engine defaults); the disk-backed config resolution is exercised separately by `HygenTemplateContextTest.testHelpersFromRealApplicationTemplateConfig` (real `.hygen.js`) and `testConfigChangeInvalidatesCache` (real VFS).

> **Corpus counts on this machine:** `/www/biorate/application-template/_templates` → 186 `.ejs.t`; `/www/biorate/core/packages/@biorate/hygen` → 29 collected real templates (the 1 remaining `.ejs.t` is the deliberate `inject-error` fixture, excluded by design).

## E. Manual sandbox checklist (task 4.2 — `./gradlew buildPlugin` + install into IDEA 2025.2)

Open the plugin project and the real template dir `/www/biorate/application-template`. Reference files below.

### E.1 Completion of variables in real templates

| File to open | Expected |
|---|---|
| `_templates/app/create/00007.00_.ls-lint.yml.ejs.t` | In the island `<%= h.root(\`${ROOT}/.ls-lint.yml\`) %>`, Ctrl+Space after `h.` offers `.hygen.js` helpers (`root`, `server`, `client`, `hygen`, `stringify`, `defined`, `platform`) alongside the catalog members; the top-level identifier completion offers `ROOT` (context variable) and `name`/`Name`/`names`/`Names`. |
| `_templates/app/create/00005.00_.gitignore.ejs.t` | Any `h.<helper>` islands resolve their helper and variables per the resolved context; no unknown-symbol squiggles. |

### E.2 JS highlighting in islands

| File to open | Expected |
|---|---|
| Any `_templates/app/create/**/*.ejs.t` with a `<%…%>` / `<%=…%>` island | The island code region is **highlighted as JavaScript** (keywords, strings, function calls), while the surrounding body is highlighted in the host language and the frontmatter keeps its own colors. Trim variants (`<%_ … _%>`, `<%- … -%>`) do not leak `_`/`-` markers into the JS range. |

### E.3 No false errors on `.hygen.js` helpers

| File to open | Expected |
|---|---|
| `_templates/app/create/00007.00_.ls-lint.yml.ejs.t` | `h.root` (a config helper surfaced as an `h` member) and unresolved-call `root(`/`stringify(` references show **no** "unknown symbol" / "unused" error markers in the injected JS. |

### E.4 Unterminated-tag stability

| Input (paste into a scratch `.ejs.t` under `_templates`) | Expected |
|---|---|
| `<%= h.changeCase.param(name` (no `%>`) | Editor stays open and usable; the **"Unterminated EJS tag"** inspection still fires; no plugin exception is logged; the unterminated island loses injection while the rest of the file keeps template highlighting. |

### E.5 Note

Task 4.2 (`./gradlew buildPlugin` + install) is the separate build/install step that must be performed before this sandbox pass. If a check above fails, record the observed behavior and return it; the failing scenario needs a fix before the change is archived.

---

# Verification — change `fork-parity-inspections` (0.4.0)

Verification run: **2026-11-12** · Command: `./gradlew test --console=plain`
This is a **delta section** appended by task 4.2. The MVP / `ejs-context-and-injection` sections above remain authoritative for their own changes; everything here covers the `fork-parity-inspections` change (multi-config `.hygen.js/.cjs/.mjs/.ts`, `setup:`/`from:` value inspections, `ts`/`_` spec-variables in completion).

## A. Full-suite results — 209 tests, 0 failures / 0 errors / 0 skipped

| Test class | Tests | Failures | Errors | Skipped | Notes |
|---|---|---|---|---|---|
| `HygenCatalogIntegrityTest` | 17 | 0 | 0 | 0 | `catalog` — integrity of frontmatter/changeCase/inflection catalogs |
| `HygenCompletionTest` | 27 | 0 | 0 | 0 | `completion` — proposal set incl. `ts`/`_` spec-variables |
| `HygenTemplateContextRealTemplatesTest` | 1 | 0 | 0 | 0 | `context` — `visibleAt` corpus sweep (never throws, engine defaults) |
| `HygenTemplateContextTest` | 30 | 0 | 0 | 0 | `context` — multi-config names, walk-up priority, `.hygen.ts` parsing, cache |
| `HygenEjsInspectionTest` | 11 | 0 | 0 | 0 | `validation` — unterminated tag, `<%%` escape, hook-file EJS |
| `HygenForkFrontmatterValueInspectionTest` | 10 | 0 | 0 | 0 | `validation` — **NEW (2.2/3.3)** `setup:`/`from:` value inspections (POS/NEG/absent/existing/nested) + **2 regression tests** (`EJS-island` dynamic/mixed setup values not flagged) |
| `HygenFrontmatterInspectionTest` | 13 | 0 | 0 | 0 | `validation` — unknown key, inject+replace, anchor w/o inject, broken delimiter |
| `HygenRealWorldTemplatesTest` | 1 | 0 | 0 | **0** | Ran (not skipped) — **zero problems** on the real `@biorate/hygen` templates (5 original inspections) |
| `HygenHighlightingTest` | 4 | 0 | 0 | 0 | `language` — token→attribute-key wiring, color settings page |
| `HygenFileTypeOverriderTest` | 6 | 0 | 0 | 0 | `host-detection` — hook files vs templates classification |
| `HygenHostDetectionTest` | 10 | 0 | 0 | 0 | `host-detection` — `detectHost` per scenario |
| `HygenLexerContinuityTest` | 4 | 0 | 0 | 0 | `language` — lexer continuity (2 fixtures + **187-file disk corpus**), editor-flash regression |
| `HygenPatternAssociatorTest` | 7 | 0 | 0 | 0 | `settings` — filename-pattern association |
| `HygenPsiTest` | 5 | 0 | 0 | 0 | `language` — file-type registration, PSI tree shape, `toValue` |
| `HygenTokenizerTest` | 6 | 0 | 0 | 0 | `language` — lexer: frontmatter/body/EJS families + trimming |
| `HygenEjsContextualNamesTest` | 9 | 0 | 0 | 0 | `injection` — `resolveRoot` / `isGroupMember` for synthetic declarations |
| `HygenEjsIslandInjectorTest` | 11 | 0 | 0 | 0 | `injection` — island JS injection incl. `testCorpusBodyAndIslandRangesNeverOverlap` |
| `HygenEjsPlaceholderInjectorTest` | 2 | 0 | 0 | 0 | `injection` — `MultiHostRegistrarPlaceholderHelper` reflective adapter |
| `HygenEjsPreludeTest` | 9 | 0 | 0 | 0 | `injection` — synthetic prelude (`h` skeleton, catalogs, helpers, engine defaults) |
| `HygenEjsReferenceProviderTest` | 3 | 0 | 0 | 0 | `injection` — reference contributor safety contract |
| `HygenInjectionTest` | 3 | 0 | 0 | 0 | `host-detection` — body injector contracts (host language + ranges) |
| `HygenPatternsTest` | 16 | 0 | 0 | 0 | `settings` — pattern list validation/UI state |
| `HygenTemplateSettingsTest` | 4 | 0 | 0 | 0 | `settings` — persistence/live association |
| **Total** | **209** | **0** | **0** | **0** | **BUILD SUCCESSFUL** |

## B. Corpus sweeps (tasks 4.2) — RAN, not skipped

1. **Continuity sweep (187 files)** — `HygenLexerContinuityTest.realWorldTemplateCorpusIsContiguousForTokenizer` / `…ForLexer` over `/www/biorate/application-template/_templates` (**187** files total, of which **186** are `.ejs.t`; the lexer continuity check collects every file under the tree). `skipped = 0` — the corpus is present and was read off disk. **0 failures.** ✅
2. **New-inspection false-positive sweep (30-file inspection corpus)** — the two NEW inspections (`HygenSetupValueInspection`, `HygenFromValueInspection`) were run over the real template corpora with the `HygenRealWorldTemplatesTest` harness (in-memory `HygenFile` via `PsiFileFactory`, `ProblemsHolder`). Results:
   - `/www/biorate/application-template/_templates` (**186** `.ejs.t`): **zero** findings — no file carries a `setup:`/`from:` key, so both inspections are silent. ✅
   - `/www/biorate/core/packages/@biorate/hygen` (**42** real templates: `src/templates/**` + `tests/fixtures/**` minus the `inject-error` subtree): the corpus check **found 1 finding** — `src/templates/init/repo/new-repo.ejs.t` `setup: <%= name %>` was flagged by `HygenSetupValueInspection`. ⚠️ **This was a genuine false positive on a dynamic value**: the raw on-screen value `<%= name %>` contains no `/`, but at generation time it renders to `name` (a CLI argument) and may legitimately be `owner/repo`. The spec scenarios only specify static values (`setup: myrepo` vs `setup: biorate/core-templates`); the dynamic (EJS-island) case was un-specified. **FIX (signed off, landed after the 0.4.0 build):** `HygenSetupValueInspection` now **skips values containing an EJS island** (or an unterminated EJS tag) — `pair.containsEjsIsland()` → `continue`. Addressed in `src/main/kotlin/com/biorate/hygen/inspection/HygenSetupValueInspection.kt` (added `containsEjsIsland()` on `HygenFrontmatterKeyValue`). Two regression tests added to `HygenForkFrontmatterValueInspectionTest` (`testDynamicSetupValueWithEjsIslandIsNotFlagged`, `testMixedStaticValueWithEjsIslandIsNotFlagged`); suite re-run at **209 tests, 0 failures / 0 errors / 0 skipped** (section A reflects the count). **Final state after the fix: new-inspection corpus findings are now 0 on both corpora.** ✅
   - `HygenFromValueInspection` over both corpora: **zero** findings (no real template uses a `from:` key; its VFS walk-up path is exercised by the 10 `HygenForkFrontmatterValueInspectionTest` unit tests against temp dirs). ✅
3. **Existing inspection zero-false-positive sweep (29→42 files)** — `HygenRealWorldTemplatesTest.testRealWorldTemplatesHaveZeroProblems` ran not-skipped (`skipped=0`) over the `@biorate/hygen` corpus with the **5 original** inspections — **zero** problems. ✅ (the new inspections are not in that test's `FILE_INSPECTIONS` list; see item 2 above.)

## C. Build & install (task 4.3)

- **Version bump** — `build.gradle.kts` `version = "0.4.0"`.
- **Build** — `./gradlew buildPlugin` → **BUILD SUCCESSFUL**; artifact `build/distributions/idea-hygen-plugin-0.4.0.zip` (jar manifest `Version: 0.4.0`, `Build-Plugin-Version: 2.20.1`, `Platform-Version: 2025.3.2`).
- **Install (initial, task 4.3)** — unpacked the zip over `~/.local/share/JetBrains/IntelliJIdea2025.2/plugins/idea-hygen-plugin/` (previous version `0.3.0` jar removed, replaced by `lib/idea-hygen-plugin-0.4.0.jar`), the procedure used for prior versions. The running IDEA on `IU-253.30387.90` loads the plugin from this directory on restart. Conditional on a restart for the plugin to become active in the running IDE.
- **Reinstall (post-fix)** — the `HygenSetupValueInspection` false-positive fix landed *after* the initial 0.4.0 build/install. `./gradlew buildPlugin` re-run → **BUILD SUCCESSFUL** (the fresh `build/distributions/idea-hygen-plugin-0.4.0.zip` is regenerated, jar confirmed to contain `HygenSetupValueInspectionKt.containsEjsIsland`). The installed `~/.local/share/JetBrains/IntelliJIdea2025.2/plugins/idea-hygen-plugin/lib/idea-hygen-plugin-0.4.0.jar` was **overwritten with the fresh jar** (same name; old file replaced), so the plugin dir now carries the fixed build — see §B item 2 for the fix description.

> **Corpus counts on this machine:** `/www/biorate/application-template/_templates` → 187 files (186 `.ejs.t`); `/www/biorate/core/packages/@biorate/hygen` → 42 collected real templates (1 remaining `.ejs.t` is the deliberate `inject-error` fixture, excluded by design).

---

# Verification — change `combined-js-injection` (0.5.0)

Verification run: **2026-11-12** · Command: `./gradlew buildPlugin` + `./gradlew test --console=plain`
This is a **delta section** appended by the 0.5.0 build. The MVP / `ejs-context-and-injection` / `fork-parity-inspections` sections above remain authoritative for their own changes; everything here covers the `combined-js-injection` change (**a single JS document per file** — the per-island `MultiHostRegistrar` injection was replaced by one file-level JS document that covers all EJS islands).
> **Scope note:** `HygenEjsIslandInjector` / `HygenEjsIslandInjectorTest` were replaced by `HygenEjsFileInjector` / `HygenEjsFileInjectorTest` for this change; the test-class count and names below reflect that.

## A. Full-suite results — 212 tests, 1 failure / 0 errors / 0 skipped

| Test class | Tests | Failures | Errors | Skipped | Notes |
|---|---|---|---|---|---|
| `HygenCatalogIntegrityTest` | 17 | 0 | 0 | 0 | `catalog` — integrity of frontmatter/changeCase/inflection catalogs |
| `HygenCompletionTest` | 27 | 0 | 0 | 0 | `completion` — proposal set incl. `ts`/`_` spec-variables |
| `HygenTemplateContextRealTemplatesTest` | 1 | 0 | 0 | 0 | `context` — `visibleAt` corpus sweep (never throws, engine defaults) |
| `HygenTemplateContextTest` | 30 | 0 | 0 | 0 | `context` — multi-config names, walk-up priority, `.hygen.ts` parsing, cache |
| `HygenEjsInspectionTest` | 11 | 0 | 0 | 0 | `validation` — unterminated tag, `<%%` escape, hook-file EJS |
| `HygenForkFrontmatterValueInspectionTest` | 10 | 0 | 0 | 0 | `validation` — `setup:`/`from:` value inspections + dynamic-value regressions |
| `HygenFrontmatterInspectionTest` | 13 | 0 | 0 | 0 | `validation` — unknown key, inject+replace, anchor w/o inject, broken delimiter |
| `HygenRealWorldTemplatesTest` | 1 | **1** | 0 | **0** | Ran (not skipped) — **1 known pre-existing environmental failure** (see below) |
| `HygenHighlightingTest` | 4 | 0 | 0 | 0 | `language` — token→attribute-key wiring, color settings page |
| `HygenFileTypeOverriderTest` | 6 | 0 | 0 | 0 | `host-detection` — hook files vs templates classification |
| `HygenHostDetectionTest` | 10 | 0 | 0 | 0 | `host-detection` — `detectHost` per scenario |
| `HygenLexerContinuityTest` | 4 | 0 | 0 | 0 | `language` — lexer continuity (2 fixtures + disk corpus), editor-flash regression |
| `HygenPatternAssociatorTest` | 7 | 0 | 0 | 0 | `settings` — filename-pattern association |
| `HygenPsiTest` | 5 | 0 | 0 | 0 | `language` — file-type registration, PSI tree shape, `toValue` |
| `HygenTokenizerTest` | 6 | 0 | 0 | 0 | `language` — lexer: frontmatter/body/EJS families + trimming |
| `HygenEjsContextualNamesTest` | 9 | 0 | 0 | 0 | `injection` — `resolveRoot` / `isGroupMember` for synthetic declarations |
| `HygenEjsFileInjectorTest` | 14 | 0 | 0 | 0 | `injection` — **NEW (0.5.0)** single-`PsiDocument`/single-language file-level JS doc with per-island ranges |
| `HygenEjsPlaceholderInjectorTest` | 2 | 0 | 0 | 0 | `injection` — `MultiHostRegistrarPlaceholderHelper` reflective adapter |
| `HygenEjsPreludeTest` | 9 | 0 | 0 | 0 | `injection` — synthetic prelude (`h` skeleton, catalogs, helpers, engine defaults) |
| `HygenEjsReferenceProviderTest` | 3 | 0 | 0 | 0 | `injection` — reference contributor safety contract |
| `HygenInjectionTest` | 3 | 0 | 0 | 0 | `host-detection` — body injector contracts (host language + ranges) |
| `HygenPatternsTest` | 16 | 0 | 0 | 0 | `settings` — pattern list validation/UI state |
| `HygenTemplateSettingsTest` | 4 | 0 | 0 | 0 | `settings` — persistence/live association |
| **Total** | **212** | **1** | **0** | **0** | **211 pass, 1 known failure** |

## B. Known pre-existing environmental failure (IGNORED — not a regression)

- **Test:** `HygenRealWorldTemplatesTest.testRealWorldTemplatesHaveZeroProblems` (`HygenRealWorldTemplatesTest.kt:63`).
- **Cause:** the real template corpus at `/www/biorate/core/packages/@biorate/hygen` currently carries **uncommitted upstream frontmatter keys** not in the plugin's `knownKeys` catalog — `glob`, `base`, `stripExtensions`, `skipIfExists`, `skip`, `transform` (24 findings, `assert expected:<0> but was:<24>`). These keys come from the upstream working tree and are **not** part of the `combined-js-injection` change or the plugin's own key set.
- **Disposition:** pre-existing, unrelated to this change; **not touched, tests not modified**. All other 211 tests pass.

## C. Build & install (task 0.5.0)

- **Version bump** — `build.gradle.kts` `version = "0.5.0"`.
- **Build** — `./gradlew buildPlugin` → **BUILD SUCCESSFUL**; artifact `build/distributions/idea-hygen-plugin-0.5.0.zip` (jar manifest `Version: 0.5.0`, `Build-Plugin-Version: 2.20.1`, `Platform-Version: 2025.3.2`, `Kotlin-Version: 2.2.20`).
- **Install** — unpacked the zip over `~/.local/share/JetBrains/IntelliJIdea2025.2/plugins/idea-hygen-plugin/` exactly as prior versions (previous `lib/idea-hygen-plugin-0.4.0.jar` removed, replaced by `lib/idea-hygen-plugin-0.5.0.jar`; jar re-verified from the install dir to carry `Version: 0.5.0`). The running IDEA on `IU-253.30387.90` loads the plugin from this directory on restart — conditional on a restart to become active.
- **Verification command** — `./gradlew test --console=plain` → **BUILD FAILED** only due to the single known environmental failure above; **211 tests passed, 0 errors, 0 skipped**.

---

# Verification — change `file-type-conflict-detector` (0.6.0)

Verification run: **2026-11-14** · Command: `./gradlew buildPlugin` (test suite state as reported by task §3.3: 239 tests)
This is a **delta section** appended by task §3.3. The MVP / `ejs-context-and-injection` / `fork-parity-inspections` / `combined-js-injection` sections above remain authoritative for their own changes; everything here covers the `file-type-conflict-detector` change (**file-type conflict detection + interactive resolution** — after each pattern sync the plugin detects hygen patterns shadowed by a foreign file type, reports one aggregated balloon, and offers per-type suppression or confirmed resolution).

## A. What changed (0.6.0)

- **Conflict detection** — after each association sync, every applied hygen pattern is re-resolved through the IDE file-type manager; any pattern that resolves to a non-hygen file type (e.g. the bundled *Env files* plugin pattern `.env.*` shadowing `.env.t`) is a conflict (`FileTypeConflictDetector`).
- **Interactive reporting** — one aggregated balloon in the **"Hygen File Type Conflicts"** notification group lists all conflicts, with a per-type **"Don't show for \<type\>"** action (persisted in `hygenFileTypeConflicts.xml` across restarts) and a single **"Resolve…"** action. The same conflict set is only reported once per session.
- **Interactive resolution** — **"Resolve…"** requires explicit user confirmation; it removes **only the exact conflicting pattern** from the shadowing file type and re-syncs the associations; patterns that cannot be removed fall back to step-by-step manual instructions (Settings → Editor → File Types).

## B. Full-suite results — 239 tests, 1 known environmental failure

| Result | Count | Notes |
|---|---|---|
| Passed | **238** | 0 errors, 0 skipped |
| Failed | **1** | `HygenRealWorldTemplatesTest.testRealWorldTemplatesHaveZeroProblems` — **known pre-existing environmental failure**, not touched: the real corpus at `/www/biorate/core/packages/@biorate/hygen` carries uncommitted upstream frontmatter keys not in the plugin's `knownKeys` catalog (`glob`, `base`, `stripExtensions`, `skipIfExists`, `skip`, `transform`). Same cause and disposition as in the 0.5.0 section (§B above); unrelated to this change. |

## C. Build & install (task §3.3)

- **Version bump** — `build.gradle.kts` `version = "0.6.0"`.
- **Build** — `./gradlew buildPlugin` → **BUILD SUCCESSFUL**; artifact `build/distributions/idea-hygen-plugin-0.6.0.zip` (jar manifest `Version: 0.6.0`, `Build-Plugin-Version: 2.20.1`, `Platform-Version: 2025.3.2`, `Kotlin-Version: 2.2.20`).
- **Install** — unpacked the zip over `~/.local/share/JetBrains/IntelliJIdea2025.2/plugins/idea-hygen-plugin/` exactly as prior versions (previous `lib/idea-hygen-plugin-0.5.0.jar` removed, replaced by `lib/idea-hygen-plugin-0.6.0.jar`; jar re-verified from the install dir to carry `Version: 0.6.0`, and its `plugin.xml` registers the `Hygen File Type Conflicts` notification group). The running IDEA on `IU-253.30387.90` loads the plugin from this directory on restart — conditional on a restart to become active.
- **Docs** — README.md / README.ru.md: added a mirrored "Conflicting file types" note (conflict detection, suppress vs resolve, manual path via Settings → Editor → File Types); installation version reference bumped to 0.6.0.

## D. MANUAL CHECKLIST (sandbox IDEA — dotenv/Env-files plugin installed)

Open the plugin project with the bundled *Env files* plugin enabled (its pattern `.env.*` shadows hygen pattern `.env.t`).

| # | Check | Expected |
|---|---|---|
| a | Restart IDEA with the dotenv plugin installed | The **"Hygen File Type Conflicts"** balloon appears (once per session), listing `.env.*` claimed by the *Env files* type. |
| b | Click **"Resolve…"** in the balloon | After confirmation, **only the `.env.*` pattern** is removed from the *Env files* file type (its other patterns untouched) and the associations re-sync — `.env.t` becomes a Hygen Template; no other Env-file behavior breaks. |
| c | Open a `.env.t` template | It gets **hygen highlighting** (frontmatter + EJS), not the Env-files rendering. |
| d | Click **"Don't show for \<Env files type\>"**, restart IDEA | The balloon **does not reappear** after the restart (suppression persisted in `hygenFileTypeConflicts.xml`). |

> If a check above fails, record the observed behavior and return it; the failing scenario needs a fix before the change is archived.
