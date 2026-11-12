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
