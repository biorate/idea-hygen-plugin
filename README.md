# Hygen Template Support

[README на русском → README.ru.md](README.ru.md)

An IntelliJ IDEA plugin for the **@biorate/hygen** code generator's templates: syntax highlighting, structural validation, completion, and language injection for template files (`.t`).

## What the plugin does

- **Template recognition** — a dedicated file type for hygen templates. The set of filename patterns is configurable (default `*.t`, with presets `*.ejs.t`, `*.tmpl`, and custom globs available); changes apply live, without restarting the IDE.
- **Syntax highlighting** — YAML frontmatter (the `--- … ---` block) and EJS tags in the template body, with configurable colors (a colors settings page plus a preview scheme).
- **Host-language injection** — the template body gets highlighting and analysis for the target language (JS/TS etc.), determined from the extension in the `to:` frontmatter value (including when the path contains EJS interpolations).
- **JavaScript injection into EJS "islands"** — code inside `<% … %>`, `<%= … %>`, and their trim variants gets full JS support: highlighting, error analysis, and completion. References in injected code resolve through synthetic declarations (no false "unknown symbol"): `h`, template context variables, and helpers from the project hygen config (`.hygen.js`/`.hygen.cjs`/`.hygen.mjs`/`.hygen.ts`). All EJS islands of a template are analyzed as a single JavaScript document, so braces or declarations spanning islands no longer raise false errors (and declarations from earlier islands are visible in later ones).
- **Inspections (tier 1, structural)**:
  - *Unknown frontmatter key* (warning) — an unknown frontmatter key (the `@biorate/hygen` fork's key set: `to`, `inject`, `sh`, `before`, `after`, `prepend`, `append`, `at_line`, `skip_if`, `skip_if_not`, `create_if_missing`, `replace`, `unless_exists`, `local`, `setup`, `force`, `message`, `echo`, `spinner`, `sh_ignore_exit`, `mode`, `verbose`, `from`, `eof_last`, `mirror`);
  - *Unterminated frontmatter block* / *Unterminated EJS tag* (error) — an unclosed frontmatter block or EJS tag;
  - *Mutually exclusive inject and replace* (error) — using `inject` and `replace` together;
  - *Anchor option without inject mode* (warning) — anchor options (`before`, `after`, `prepend`, `append`, `at_line`) without `inject` mode;
  - *EJS tags in hook file* (warning) — EJS tags in hook files (`prompt.js`/`index.js`/`prompt.ts`/`index.ts`), where the engine does not process them;
  - *Setup value without owner/repo* (warning) — a `setup:` value without the `owner/repo` reference shape the engine expects (it throws `SetupRepositoryError`);
  - *From file not found* (warning) — a `from:` value whose file does not exist relative to the nearest `_templates` directory (silent when no `_templates` is found).
- **Completion** — frontmatter keys with descriptions of the fork's semantics; members of the `h` object (`h.changeCase.*` — 40 methods, `h.inflection.*` — 16 methods, plus `h.capitalize`, `h.path`, and others); template context variables, including the engine spec-variables `ts` (run timestamp) and `_` (positional CLI arguments). That's 25 frontmatter keys and 56 `h` members in total.
- **Template context** — the plugin understands which variables are available in a template: engine defaults (`name`, `Name`, `names`, `Names`, plus the spec-variables `ts` and `_`), `localsDefaults` from the project hygen config, variables requested in prompt hooks, and variables declared in the template itself.

## Requirements

- IntelliJ IDEA **2024.3+** (`since-build 243`).
  - Basic features (highlighting, inspections, frontmatter completion) work in **Community Edition**.
  - JavaScript/TypeScript injection (into the template body and EJS islands) requires **IDEA Ultimate** — it relies on the JavaScript plugin.
- The plugin is built from source: **it is not yet published on the Marketplace**.

## Installation

1. Build the plugin:
   ```bash
   ./gradlew buildPlugin
   ```
2. The artifact appears in `build/distributions/idea-hygen-plugin-<version>.zip` (current version — `0.6.0`).
3. In the IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…** and pick the zip.
   Alternative: unpack the zip and copy the plugin directory into the IDE's plugins folder (`~/.local/share/JetBrains/IntelliJIdea<version>/plugins` etc.).
4. Restart the IDE.

## Configuration

**Settings → Tools → Hygen Templates**:

- An editable list of filename patterns for hygen templates, with presets:
  - `*.t` (default),
  - `*.ejs.t`,
  - `*.tmpl`,
  - plus any custom patterns. Input is validated; changes apply immediately, without restarting the IDE.
- **Conflicting file types** — if another file type claims the same filename patterns as your hygen template patterns (for example, the bundled *Env files* plugin with the pattern `.env.*` shadowing a `.env.t` template pattern), the plugin shows a **"Hygen File Type Conflicts"** notification listing each conflicting pattern and the file type that shadows it. From the notification you can either **"Don't show for \<file type\>"** (suppress the warning for that type; persisted across restarts) or **"Resolve…"** — after your explicit confirmation the plugin removes only the conflicting pattern from the other file type and re-syncs the associations; patterns that could not be removed automatically fall back to step-by-step manual instructions. The same conflict can always be resolved by hand via **Settings → Editor → File Types → \<the conflicting type\> → File name patterns**.
- Files inside `_templates/**` (within an action folder) are **all** considered templates, except `prompt.js` / `index.js` / `prompt.ts` / `index.ts` — those stay ordinary JS/TS files (hook files). This matches the engine's own behavior.
- ⚠️ Note: the default pattern `*.t` claims the `.t` extension IDE-wide — if you have other `.t` files, narrow the pattern list.

## Template syntax quick reference

**Frontmatter** — a YAML block at the top of the file:

```yaml
---
to: app/emails/<%= name %>.ts
inject: true
after: // add-email-here
---
```

Key keys: `to`, `inject`, `sh`, `before`, `after`, `prepend`, `append`, `at_line`, `skip_if`, `skip_if_not`, `create_if_missing`, `replace`, `local`, and more. The full list of keys and their semantics is in the engine documentation (links below).

**EJS tags** in the template body:

| Tag | Purpose |
|---|---|
| `<%= expression %>` | interpolation with escaping |
| `<%- expression %>` | interpolation without escaping |
| `<% code %>` | scriptlet (executable JS) |
| `<%# comment %>` | comment |
| `<%-`, `-%>`, `<%_`, `_%>` | whitespace/newline trim markers |
| `<%%` | escaping a literal `<%` |

**`h` helpers** — available inside EJS tags: `h.changeCase.*` (the [change-case](https://www.npmjs.com/package/change-case) package, 40 methods), `h.inflection.*` (the [inflection](https://www.npmjs.com/package/inflection) package, 16 methods), `h.capitalize`, `h.path`, and others.

**Context variables**: `name`, `Name`, `names`, `Names` (engine defaults, always available), the engine spec-variables `ts` (run timestamp) and `_` (positional CLI arguments), variables from `localsDefaults` in the project hygen config (`.hygen.js`/`.hygen.cjs`/`.hygen.mjs`/`.hygen.ts`), variables requested in a prompt hook (`prompt.js`), and variables declared in the template itself.

## Links

- Template engine: **@biorate/hygen** — a fork of Hygen ([jondot/hygen](https://github.com/jondot/hygen)), living in the [biorate/core](https://github.com/biorate/core) monorepo, directory [`packages/@biorate/hygen`](https://github.com/biorate/core/tree/main/packages/%40biorate/hygen).
- [change-case](https://www.npmjs.com/package/change-case) — case-conversion library, available as `h.changeCase.*`.
- [inflection](https://www.npmjs.com/package/inflection) — word-form library (pluralization etc.), available as `h.inflection.*`.

## Development

```bash
./gradlew test        # tests (JUnit + platform test framework)
./gradlew buildPlugin # build the distribution
./gradlew runIde      # run an IDE sandbox with the plugin
```

The project follows the OpenSpec process: feature specifications live in `openspec/specs/` (template language, validation, host-language detection, config resolution, EJS injection, completion, template context), and manual/automated verification results are in `VERIFICATION.md`.
