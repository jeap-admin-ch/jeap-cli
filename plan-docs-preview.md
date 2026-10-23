# Plan: documentation preview

## Context

Teams write their documentation as Markdown in their repositories, in two shapes:

- Dedicated docs repositories such as `BAZG-ApplicationPlatform/applicationplatform-docs` and `BIT-JME/jme-nivel-docs`:
  arc42 chapter folders (`docs/<n>-<chapter>/*.md`), uploaded to the jEAP doc service by the reusable workflow
  `NIVEL-GITHUB/jeap-github-actions/.github/workflows/jeap-doc.yml`.
- A `docs/` folder in a service repository, such as
  `BAZG-ApplicationPlatform/applicationplatform-initializer-service` (flat `docs/*.md`, no doc service upload, no docs
  workflow).

Today a writer sees the rendered result only after the doc service published it, minutes later and for whichever
branch uploaded last (ADR-003 in both docs repositories accepts this as a drawback). There is no local preview and no
preview per pull request.

Goal: a simple preview of exactly the Markdown pages in a repository's docs folder, nothing more:

1. **Local:** `jeap docs preview` renders the docs, serves them with a built-in web server, opens the browser and
   reloads the page when a file changes.
2. **Pull request:** a reusable GitHub Actions workflow publishes the preview to the repository's (private) GitHub
   Pages site and posts the link on the pull request.

The jEAP CLI runs as a native binary on the host (Linux x86_64, macOS arm64) since 2.0.0, so it can bind a local port
and start the browser directly.

## Agreed decisions

- **Rendering in the jEAP CLI (Java)**, not with the doc service's Docusaurus template. No Node.js on developer
  machines or in the preview job, no dependency on the doc service's internal content contract, builds in
  milliseconds, the same command runs locally and in CI.
- **Diagrams render in the browser**, as on the published site: `mermaid` with mermaid.js, `plantuml` and Graphviz
  with `@plantuml/core` (the engine the doc service's PlantUML plugin uses). Committed images (draw.io PNG/SVG
  exports etc.) are shown as they are. No live draw.io rendering.
- **Broken links are ignored.** The docs in a repository are not the complete site: generated chapters exist only on
  the doc service, so a link can be valid even though it does not resolve in the preview. No warnings either.
- **Auto reload** in the local preview.
- **Pull request trigger:** a pull request whose title contains `preview` (case-insensitive) gets a preview when it is
  opened, and the preview is updated on new commits. Closing the pull request removes the preview, if it is still
  the one shown.
- **One preview per repository at a time.** GitHub Pages has one site per repository; PR previews in GitHub Pages are
  an internal alpha, and workarounds with legacy branch-based Pages publishing are not wanted. Every page shows from
  which branch and pull request the preview comes.
- **GitHub Pages:** private Pages are enabled and work in the Nivel enterprise.
- **Runner:** the preview job runs on the CodeBuild runner, like `jeap-doc.yml`.

## Facts this plan builds on

Verified on 2026-10-23 against the default branches:

- **Doc service rendering:** `jeap-doc-service` renders with Docusaurus 3.10.2 from the `jeap-doc-site` template,
  `markdown.format: 'md'` (CommonMark, no MDX), raw HTML escaped (`remark-escape-raw-html`), Mermaid via
  `@docusaurus/theme-mermaid`, PlantUML and Graphviz via `@matfsw/docusaurus-plantuml-plugin` 1.8.8
  (`jeap-doc-site/src/main/resources/site/docusaurus.config.js:361-401`). The template needs Node 24 and the
  `content/site.json` / `content/environments.json` the service's generator writes; it is not a standalone tool.
- **Docs conventions** (`applicationplatform-docs/README.md`, `jeap-doc-service/docs/custom-documentation.md`): front
  matter `title` and `description`; links between pages with the `.md` extension; diagrams as fenced `plantuml`,
  `mermaid` or `graphviz` blocks; images `png/jpg/jpeg/gif/webp/avif/svg`; no files or folders starting with `.` or
  `_`. Both docs repositories currently use 12 `plantuml` fences and no `!include` or `!theme`.
- **`@plantuml/core` 1.2026.9** (MIT since 1.2026.6): PlantUML compiled to JavaScript with TeaVM. Needs
  `plantuml.js` (ES module, 3.4 MB), `viz-global.js` (Graphviz/Viz.js, classic script, 1.4 MB) and `themes.js`
  (0.3 MB, loaded on demand for `!theme`). API: `render(lines, targetId, {dark})` and
  `renderToString(lines, onSuccess, onError, options)`. The PlantUML standard library (`!include <C4/...>`) is not
  bundled and would be fetched from `PLANTUML_STDLIB_BASE`; not needed for the current docs.
- **mermaid 11.17.2** (MIT): `dist/mermaid.min.js` (IIFE, about 3 MB).
- **CodeBuild image:** `jeap-codebuild-java:21-node-22` is based on Amazon Linux 2023 (glibc 2.34). The Linux binary
  of the jEAP CLI 2.0.0 branch build (built on `ubuntu-latest`) needs at most `GLIBC_2.34`, so it runs there, with no
  margin.
- **jeap-cli conventions:** commands are Spring Shell `@Command` methods with `@Option(longName = ...)`
  (`commands/PcsCommands.java`); native resources are included with `-H:IncludeResources` build args
  (`jeap-cli/pom.xml`, the `banner/.*` entry); the launcher `exec`s the binary with the user's working directory.

## Part 1: the CLI

### Commands

New class `commands/DocsCommands.java`, group `Docs`:

- `jeap docs preview` - render, serve, open the browser, reload on change; runs until Ctrl+C.
  - `--source` (default `docs`): the docs folder, relative to the working directory.
  - `--port` (default `4000`): if the port is taken, use a free port and say so.
  - `--no-browser` (default `false`): only print the link.
- `jeap docs build` - write the preview as a static site, used by the pull request workflow.
  - `--source` (default `docs`).
  - `--output` (required): target folder; it is emptied first (refuse if it is the source folder or contains it).
  - `--title` (default: name of the git top-level folder, else of the working directory).
  - `--banner` (optional): text shown at the top of every page, e.g.
    `Preview of branch feature/x · PR #12 · 1a2b3c4`.

A missing or empty source folder is an error with a clear message. Both commands share one renderer; `preview` uses
`--title` the same way and shows the banner `Local preview of <absolute source path>`.

### Rendering

Package `ch.admin.bit.jeap.cli.docs`:

- **Discovery:** walk the source folder; skip entries starting with `.` or `_`. `*.md` files are pages, everything
  else is a static file served as it is (relative path preserved).
- **Markdown:** commonmark-java 0.28.0 (`org.commonmark:commonmark` plus `commonmark-ext-gfm-tables`,
  `-gfm-strikethrough`, `-autolink`, `-yaml-front-matter`, `-heading-anchor`; autolink pulls in
  `org.nibor.autolink:autolink`). Raw HTML is escaped (`HtmlRenderer.builder().escapeHtml(true)`), like the doc
  service does. Use one `Parser` and one extension list for parsing and rendering.
- **Titles:** front matter `title` (read with `YamlFrontMatterVisitor`; strip surrounding `'`/`"`, the visitor returns
  the raw value), else the text of the first level-1 heading (collect `Text` and `Code` children), else the file name
  without extension and numeric prefix, `-`/`_` replaced by spaces, first letter upper-case. If the page has a front
  matter title and no level-1 heading, render the title as `<h1>` (as Docusaurus does).
- **Links:** before rendering, an `AbstractVisitor` over `Link` nodes rewrites relative destinations ending in `.md`
  (optionally followed by `#fragment`) to `.html`. Leave destinations with a URI scheme
  (`^[a-zA-Z][a-zA-Z0-9+.-]*:`), starting with `/` or only a `#fragment` unchanged. Images are not rewritten. No link
  checking.
- **Heading ids:** the `HeadingAnchorExtension` ids (lower-case, spaces to `-`, punctuation dropped, duplicates get
  `-1`, `-2`), close to GitHub's, so `#fragment` links within and between pages work.
- **Diagrams:** register a custom `NodeRenderer` for `FencedCodeBlock` with
  `HtmlRenderer.builder().nodeRendererFactory(...)`. It replaces the core rendering of all fenced blocks, so it must
  also write the ordinary ones. The language is the first word of the info string, lower-cased. Markup (attribute
  values written with `HtmlWriter.tag(name, attributes)` are escaped by commonmark):
  - `mermaid`: `<pre class="mermaid">source</pre>`
  - `plantuml`, `puml`: `<div class="diagram plantuml" data-source="source"><pre>source</pre></div>`
  - `dot`, `graphviz`, `gv`: `<div class="diagram graphviz" data-source="source"><pre>source</pre></div>`
  - anything else: `<pre><code class="language-x">source</code></pre>`, no syntax highlighting.

  The visible `<pre>` keeps the source readable until the browser has rendered the diagram, and without JavaScript.
- **Navigation:** a sidebar mirroring the folder tree, each folder a `<details>` element that is open when it contains
  the current page. Folders and pages sort by a leading number (`2-` before `10-`), then alphabetically by title
  (case-insensitive). Folder labels: `1-introduction-and-goals` becomes `1. Introduction and goals`. A folder's
  `README.md` or `index.md` is the folder's own page (the link in the folder's `<summary>`). Folders without pages are
  left out. The root `README.md`/`index.md` is the start page and not listed. The current page is highlighted.
- **Start page:** `README.md` or `index.md` in the source root if present, else a generated overview listing all
  pages per folder.
- **Page layout:** one HTML template with a sticky header (site title linking to the start page, banner), the sidebar
  and the content (max. width about 880 px). Embedded CSS, light and dark mode via `prefers-color-scheme`. Below
  860 px the sidebar is hidden behind a toggle button in the header. Tables get borders and horizontal scrolling.
  Pages use relative URLs only, so the site works under any base path, e.g. a private Pages domain. Every page
  carries `<meta name="preview-assets" content="<../ per folder level>_preview/">`; the scripts build their URLs from
  it.
- **Output:** each page `a/b.md` becomes `a/b.html`; the start page `index.html`; the CLI's own assets go to
  `_preview/` (no clash with docs, which may not use names starting with `_`).
- **Effort per request:** the sidebar needs the title of every page, so a request parses all pages. That is
  instantaneous for today's docs (`applicationplatform-docs` has about 35 pages); no cache needed.

### Browser-side diagram rendering

`_preview/preview.js` (own code, an ES module, about 100 lines; `plan-docs-preview-poc/assets/preview.js` is a working
starting point):

- `dark = matchMedia('(prefers-color-scheme: dark)').matches`; the assets prefix comes from the `preview-assets` meta.
- Load only what the page needs: `mermaid.min.js` only if there is a `pre.mermaid`; `viz-global.js` (classic script,
  defines `globalThis.Viz`) once if there is a PlantUML or Graphviz block, before rendering either of them.
- Mermaid: `mermaid.initialize({startOnLoad: false, theme: dark ? 'dark' : 'default', securityLevel: 'strict'})`.
  Wrap each `pre.mermaid` in a `div.diagram`, then render each one with `await mermaid.run({nodes: [block]})` in its
  own `try`/`catch`, so one broken diagram doesn't stop the others.
- PlantUML: `const {renderToString} = await import(new URL(assets + 'vendor/plantuml.js', location.href).href)`. For
  each placeholder, take `data-source`, drop one trailing newline, split into lines, and wrap them in
  `@startuml`/`@enduml` if no line starts with `@start` (the docs repositories always write `@startuml`, but bare
  fences are common elsewhere). Call `renderToString(lines, onSuccess, onError, {dark})` one after the other and put
  the SVG into the placeholder.
- Graphviz: `const viz = await Viz.instance()`, then `viz.renderSVGElement(source)` per placeholder. Graphviz SVGs have
  black strokes on a transparent background; give them a white background in CSS so they stay readable in dark mode.
- Render the three kinds in parallel (`Promise.all`); log unexpected failures to the console.
- Errors:
  - PlantUML reports **syntax errors through `onSuccess`**, as its own error image ("Syntax Error?" with the line
    highlighted). That is good enough and the same as everywhere else PlantUML runs. `onError` only fires when the
    engine fails.
  - When `onError` fires, Mermaid throws, or Graphviz throws, replace the block with a red box with the message above
    the diagram source. The rest of the page still renders.
- Sanitize the PlantUML and Graphviz SVG with DOMPurify before inserting it, as the doc service's PlantUML plugin
  does (`dompurify` is one of its dependencies). Mermaid sanitizes itself with `securityLevel: 'strict'`. Previews
  are published to GitHub Pages and contain other people's pull request content, so treat it as untrusted.
- `@plantuml/core` needs a moment to initialize; PlantUML diagrams appear a little after Mermaid and Graphviz, which
  is why the source stays visible until the SVG is there.

### Local preview server

- JDK `com.sun.net.httpserver.HttpServer`, bound to `InetAddress.getLoopbackAddress()` (127.0.0.1) only, with
  `Executors.newVirtualThreadPerTaskExecutor()` as executor: every open tab holds one handler for its event stream.
- Routes: `/_preview/events` (event stream), `/_preview/vendor/*` and `/_preview/*` (bundled assets from the class
  path), `/` and `/index.html` (start page), `*.html` (render the matching `.md`; 404 if there is none), everything
  else a static file from the source folder.
- Pages are rendered on each request from the current files, so they are always fresh; no output folder.
- Every response gets `Cache-Control: no-store`, so a reload always fetches fresh pages and images.
- Static files are served from the source folder with a content type by extension (`html`, `css`, `js`, `svg`, `png`,
  `jpg`/`jpeg`, `gif`, `webp`, `avif`, `json`, `txt`, else `application/octet-stream`).
- **Path traversal:** `HttpExchange.getRequestURI().getPath()` is already percent-decoded. Resolve it against the real
  path of the source folder (`toRealPath()`), `normalize()`, and require `startsWith(root)`; if the file exists,
  require that its `toRealPath()` also starts with the root (symlinks pointing out). Anything else is a 404. The PoC
  verified that `/../../../etc/passwd` and `/%2e%2e/%2e%2e/etc/passwd` (sent with `curl --path-as-is`) return 404.
- **Auto reload:** a daemon thread computes a fingerprint of the source tree every 500 ms (relative path, size and last
  modified time of every file) and on a change notifies the browsers over server-sent events:
  - `/_preview/events` answers `Content-Type: text/event-stream` with `sendResponseHeaders(200, 0)` (streamed) and
    first writes the comment `: connected`.
  - Each connection gets its own `BlockingQueue`; the poller offers `change` to all queues. The handler waits on its
    queue for up to 15 s and writes `data: change` or the comment `: ping`. The ping detects closed tabs: the write
    fails and the handler removes its queue.
  - `_preview/reload.js` (only included by `preview`, not by `build`) reloads the page on a message, and also when the
    connection reopens after an error, which covers a restarted preview. It keeps the scroll position across reloads
    in `sessionStorage` (guarded with `try`/`catch`).
  - `reload.js` does nothing when the URL has the query parameter `noreload`. Headless browser checks need this: an
    open event stream keeps Chrome's `--virtual-time-budget` from ever settling (see Verification).
  - Polling instead of `WatchService`: the JDK's `WatchService` on macOS polls with a delay of several seconds and
    needs one registration per folder; docs trees are small.
- **Browser:** after the server is up, print `Docs preview: http://127.0.0.1:<port>/ (Ctrl+C to stop)` and open the
  URL: macOS `open`; Linux in WSL (`/proc/version` contains `microsoft`) `wslview`, falling back to
  `cmd.exe /c start`; other Linux `xdg-open`, but only if `DISPLAY` or `WAYLAND_DISPLAY` is set. Failures to open are
  ignored; the link is always printed. `--no-browser` skips opening.
- The command blocks until the process is interrupted; a shutdown hook stops the server and the poller.

### Bundled JavaScript

- `jeap-cli/src/main/docs-preview/package.json` and `package-lock.json` pin `mermaid` and `@plantuml/core` (exact
  versions). Renovate keeps them up to date and Trivy scans the lockfile.
- The Maven build installs them with `frontend-maven-plugin` (`install-node-and-npm`, `npm ci`; Node only at build
  time, inside `target/`) and copies `mermaid/dist/mermaid.min.js` and `@plantuml/core/{plantuml.js,viz-global.js,
  themes.js}` into `target/classes/docs-preview/vendor/`. Own assets (template, CSS, `preview.js`, `reload.js`) live
  in `src/main/resources/docs-preview/`.
- Native image: add `-H:IncludeResources=docs-preview/.*` to the build args next to `banner/.*`. The binary grows by
  about 8.7 MB (`mermaid.min.js` 3.57 MB, `plantuml.js` 3.36 MB, `viz-global.js` 1.45 MB, `themes.js` 0.33 MB); it
  is about 82 MB today.
- Bundle `dompurify` the same way (`dist/purify.min.js`, about 20 KB).
- The CLI never loads scripts from a CDN; previews work offline.
- Licenses: both libraries are MIT. Make sure they appear in `THIRD-PARTY-LICENSES.md` (check whether the open source
  compliance action picks up npm dependencies; if not, add them by hand).

## Part 2: pull request previews

### Reusable workflow

New workflow `NIVEL-GITHUB/jeap-github-actions/.github/workflows/jeap-doc-preview.yml` with its documentation
`doc/jeap-doc-preview.md`, following the conventions of `jeap-doc.yml` (actions pinned by commit SHA, `inputs` with
descriptions, runner label). It is independent of `jeap-doc.yml`, so repositories without a doc service upload can
use it.

Inputs:

- `system-name` (required): used in the CodeBuild runner label, as in `jeap-doc.yml`.
- `docs-path` (default `docs`).
- `cli-version` (default empty: latest release of the jEAP CLI).
- `codebuild-image`, `codebuild-image-registry-account-id`, `aws-region`: as in `jeap-doc.yml`.

Caller in a repository, e.g. `.github/workflows/docs-preview.yml`:

```yaml
name: docs-preview

on:
  pull_request:
    types: [opened, reopened, edited, synchronize, closed]

permissions:
  contents: read
  pages: write
  id-token: write
  pull-requests: write
  deployments: read

jobs:
  preview:
    uses: NIVEL-GITHUB/jeap-github-actions/.github/workflows/jeap-doc-preview.yml@v1
    with:
      system-name: applicationplatform
```

The title condition lives inside the reusable workflow, so the caller stays this small.

### Event handling

| Event                       | Title contains `preview`             | Action                |
|-----------------------------|--------------------------------------|-----------------------|
| `opened`, `reopened`        | yes                                  | publish               |
| `synchronize` (new commits) | yes                                  | publish               |
| `edited`, title changed     | yes now                              | publish               |
| `edited`, title changed     | no longer                            | remove                |
| `closed` (merged or not)    | any                                  | remove                |
| anything else               | no, or `edited` without title change | nothing (job skipped) |

`edited` also fires for body changes; act only if `github.event.changes.title` is set. `contains()` in GitHub
expressions is case-insensitive.

All jobs share one concurrency group per repository (`jeap-doc-preview-${{ github.repository }}`,
`cancel-in-progress: false`), so publish and remove never run at the same time and the last event wins.

### Job `publish`

Runs on the CodeBuild runner, `environment: github-pages`.

1. Check out the pull request head (`ref: ${{ github.event.pull_request.head.sha }}`).
2. Install the jEAP CLI: resolve the version (input, or the redirect of
   `https://github.com/jeap-admin-ch/jeap-cli/releases/latest`), download `jeap-cli-linux-x86_64` and its `.sha256`
   from the release, verify the checksum, `chmod +x`.
3. `jeap-cli docs build --source <docs-path> --output _site --title <repository name>
   --banner "Preview of branch <head ref> · PR #<number> · <short sha>"`.
4. `actions/upload-pages-artifact` with `_site`, then `actions/deploy-pages`; its `page_url` output is the link.
5. Create or update one comment on the pull request, found by the hidden marker `<!-- jeap-doc-preview -->`
   (`actions/github-script`): link, branch, commit, time, and the note that the repository has one preview at a time,
   so a newer preview of another pull request replaces it.

### Job `remove`

GitHub Pages cannot delete a deployment; `DELETE /repos/{owner}/{repo}/pages` would disable Pages altogether. So
"remove" means replacing the preview with a placeholder page, and only if the site still shows this pull request's
preview:

1. Find out whether the current Pages deployment belongs to this pull request: read the latest deployment of the
   `github-pages` environment (`GET /repos/{owner}/{repo}/deployments?environment=github-pages&per_page=1`) and
   compare it with this pull request. Verify during implementation which `ref`/`sha` a deployment from a
   `pull_request` run records (probably `refs/pull/<n>/merge` and the merge commit). If that is not reliable, compare
   with the commit the publish job wrote into the PR comment.
2. If it belongs to this pull request: build a placeholder page ("No documentation preview is active. The last
   preview, of branch X from PR #n, was removed when the pull request was closed.") and deploy it like in `publish`.
   The placeholder can be a static file in the workflow repository; it doesn't need the CLI.
3. Update the PR comment: "Preview removed", or "Preview was replaced by a newer one" if the site showed another pull
   request.

### Repository setup (once per repository, needs admin rights)

- Settings → Pages: source **GitHub Actions**, visibility **private**.
- Settings → Environments → `github-pages`: GitHub creates it with a deployment branch rule that only allows the
  default branch. Allow the pull request branches (e.g. "No restriction"). Verify how the rule evaluates
  `pull_request` runs, whose ref is `refs/pull/<n>/merge`.
- Add the caller workflow.

Pull requests from forks get a read-only token and cannot deploy; that's acceptable for internal repositories.

## Work packages

Implement in this order; every package leaves the build green.

1. **Renderer** (`docs` package): discovery, Markdown, titles, link rewriting, heading ids, diagram placeholders,
   navigation, page template, own assets. Start the assets from `plan-docs-preview-poc/assets/` (CSS, `preview.js`,
   `reload.js`) and add DOMPurify. Move `plan-docs-preview-poc/sample-docs/` to
   `jeap-cli/src/test/resources/docs-preview/sample-docs/` as a test fixture; it covers each diagram type, a
   draw.io-style PNG, `.md` links with and without fragment, a link to a page that only exists on the doc service,
   escaped raw HTML, strikethrough and autolinks. Unit tests with that fixture and `@TempDir` docs trees: arc42 layout
   and flat layout, front matter titles (quoted and unquoted), `.md` link rewriting with fragments, escaped raw HTML,
   diagram fences and their markup, sort order (`2-` before `10-`), folder pages, start page with and without
   `README.md`.
2. **`jeap docs build`** writing the static site, plus tests on the output tree.
3. **Bundled JavaScript**: `package.json`/lockfile, `frontend-maven-plugin`, resource copy, native image resource
   pattern, third-party licenses.
4. **`jeap docs preview`**: server, path traversal protection, server-sent events, poller, browser opening,
   `--port` fallback, shutdown. Tests for path resolution and change detection; the browser opener behind a small
   interface so tests don't open browsers.
5. **CLI documentation**: `docs/docs-preview.md` (usage, what is and isn't rendered), README command list,
   CHANGELOG. The branch is based on the unreleased 2.0.0; if both ship together this is part of 2.0.0, otherwise
   bump to 2.1.0-SNAPSHOT (minor, new feature).
6. **Workflow** in `jeap-github-actions` (separate repository and pull request): `jeap-doc-preview.yml`, placeholder
   page, `doc/jeap-doc-preview.md`. Test it in a sandbox repository first.
7. **Adoption**: Pages setup and caller workflow in `applicationplatform-docs` and
   `applicationplatform-initializer-service`; mention the preview in their README.

## Verification

- `./mvnw test` green; native build on Linux and macOS in CI.
- Manually, with the native binary on Linux and macOS:
  1. `jeap docs preview` in `applicationplatform-docs` and in `applicationplatform-initializer-service`: browser
     opens, sidebar matches the folder tree, PlantUML and Mermaid diagrams render, images show, `.md` links work.
  2. Edit a page and add a new one: the browser reloads within about a second, the new page appears in the sidebar.
  3. Stop and restart the preview: the open tab reconnects and reloads.
  4. Port 4000 taken: a free port is used and printed.
  5. Path traversal: `curl http://127.0.0.1:<port>/../../etc/passwd` and an encoded variant return 404.
  6. No network: diagrams still render.
  7. Dark mode of the OS: page and diagrams are readable (not checked in the PoC).
  8. A PlantUML diagram with `!theme` renders themed (checks that `themes.js` is found; not checked in the PoC).
- Automated look at a rendered page without the Chrome extension, as done for the PoC (headless Chrome is installed
  on the development machine):

  ```bash
  google-chrome --headless=new --disable-gpu --no-first-run --hide-scrollbars --user-data-dir="$(mktemp -d)" \
    --window-size=1400,2400 --virtual-time-budget=30000 --screenshot=diagrams.png \
    'http://127.0.0.1:4000/2-diagrams/diagrams.html?noreload'
  ```

  Without `--virtual-time-budget` the screenshot is taken before PlantUML has finished; without `?noreload` the open
  event stream keeps the budget from settling and Chrome never writes the screenshot. `--dump-dom` has the same
  problem. Pages written by `jeap docs build` have no event stream and need no parameter.
- Pull request workflow in a sandbox repository: open a PR with `preview` in the title (published, comment with
  link), push a commit (updated), rename the title without `preview` (removed), a second PR replaces the first
  preview (first PR's close doesn't remove the second one's preview), close the PR (removed).
- In CI, check that the Linux binary still needs at most `GLIBC_2.34` (`objdump -T | grep GLIBC_`), so it keeps
  running on the Amazon Linux 2023 CodeBuild image; build on an older Ubuntu runner if that changes.

## Out of scope

- Fidelity with the published site: Docusaurus look, the doc service's remark extensions (`:::details`, environment
  links, grouped tables, superscript), arc42 chapter mapping, generated chapters, search.
- Link checking and content validation (the docs workflow validates).
- Syntax highlighting of code blocks.
- PlantUML `!include` of local files and the PlantUML standard library (C4 etc.).
- Live draw.io rendering.
- More than one preview per repository at a time.

## Open points

- Which ref/sha a `github-pages` deployment from a `pull_request` run records, and how the `github-pages`
  environment's branch rule treats `pull_request` runs (see the workflow jobs above).
- Whether the CodeBuild runner can download release assets from `github.com` (redirects to
  `objects.githubusercontent.com`) through its network setup.

## Proof of concept

A single-file PoC confirmed the approach and was reviewed in the browser ("looks great"). Its sources are in
`plan-docs-preview-poc/` as reference for the implementation; delete the folder together with this plan once the
feature is implemented.

- `DocsPreviewPoc.java`: renderer and server in one class (commonmark-java 0.28.0 with the extensions above, JDK
  `HttpServer` with virtual threads, rendering on every request, server-sent events with 500 ms polling).
- `assets/`: `preview.css`, `preview.js` (Mermaid 11.17.2, `@plantuml/core` 1.2026.9 and Graphviz in the browser),
  `reload.js`.
- `sample-docs/`: the fixture described in work package 1.

Run it (JDK 25; the vendor folder must contain `mermaid.min.js`, `plantuml.js`, `viz-global.js` and `themes.js`
from the npm packages):

```bash
mvn dependency:get -Dartifact=org.commonmark:commonmark-ext-<name>:0.28.0   # for each extension and the core
javac -d classes -cp "<commonmark jars>:<autolink jar>" DocsPreviewPoc.java
java -cp "classes:<commonmark jars>:<autolink jar>" DocsPreviewPoc \
  <docs folder> <port> <title> <banner> <vendor folder> plan-docs-preview-poc/assets
```

Results:

- Rendered `applicationplatform-docs` (arc42 layout, PlantUML deployment diagrams, tables, front matter titles) and
  the flat `applicationplatform-initializer-service/docs` without changes to the docs.
- PlantUML, Mermaid and Graphviz render in the browser; a committed PNG shows; `.md` links with fragments work; raw
  HTML is escaped; a PlantUML syntax error shows PlantUML's own error image.
- A file change sends `data: change` to the open tabs within about half a second.
- Path traversal attempts return 404.

Differences between the PoC and this plan, all deliberate:

- The PoC has no DOMPurify, no `build` command, no port fallback, doesn't open the browser itself, and reads its
  assets from the file system instead of the class path.
- It escapes HTML with its own helper.
- It doesn't validate that the source folder exists.
