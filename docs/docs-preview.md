# Docs Preview

Previews the Markdown documentation of a repository: `jeap docs preview` shows the pages of the docs folder in the
browser and reloads them when a file changes, `jeap docs build` writes the same pages as a static site, e.g. for a
preview of a pull request.

The preview shows exactly the Markdown pages in the docs folder, rendered close to the jEAP doc service: CommonMark
with tables, front matter titles, Mermaid, PlantUML and Graphviz diagrams and committed images. It does not look like
the published site and does not contain the chapters the doc service generates.

## Preview Locally

Navigate to the root directory of your repository and run:

```bash
jeap docs preview
```

The CLI renders the pages of the `docs` folder, serves them on `http://127.0.0.1:4000/` and opens the browser. When
you save a file in the docs folder, the open pages reload within about a second. Stop the preview with Ctrl+C.

| Option         | Default                       | Description                                                     |
|----------------|-------------------------------|-----------------------------------------------------------------|
| `--source`     | `docs`                        | Docs folder, relative to the working directory                  |
| `--port`       | `4000`                        | Port on 127.0.0.1; if it is taken, a free port is used          |
| `--no-browser` | `false`                       | Only print the link, do not open the browser                    |
| `--title`      | name of the repository folder | Site title shown in the header                                  |

Examples:

```bash
jeap docs preview --source documentation      # docs in another folder
jeap docs preview --port 4001 --no-browser    # another port, open the link yourself
```

The preview is only reachable from your own machine. In WSL, the browser opens through `wslview` or Windows' `start`.
On Linux without a graphical session, open the printed link yourself.

## Build a Static Site

```bash
jeap docs build --output _site --banner "Preview of branch feature/x · PR #12"
```

| Option     | Default                       | Description                                            |
|------------|-------------------------------|--------------------------------------------------------|
| `--source` | `docs`                        | Docs folder, relative to the working directory         |
| `--output` | (required)                    | Output folder; it is emptied first                     |
| `--title`  | name of the repository folder | Site title shown in the header                         |
| `--banner` |                               | Text shown at the top of every page                    |

Every page `a/b.md` becomes `a/b.html`, the start page `index.html`. Images and other files are copied as they are,
the preview's scripts go to `_preview/`. All links are relative, so the site works under any path, e.g. on GitHub
Pages. The output folder must not be the docs folder, contain it or be inside it.

## What Is Rendered

- **Pages:** every `*.md` file. Files and folders starting with `.` or `_` are ignored, like on the doc service.
- **Navigation:** the sidebar mirrors the folder tree. Folders and pages are sorted by a leading number (`2-` before
  `10-`), then by title; `1-introduction-and-goals` is shown as `1. Introduction and goals`. A folder's `README.md`
  or `index.md` is the folder's own page.
- **Start page:** `README.md` or `index.md` in the docs folder, else an overview of all pages.
- **Titles:** the front matter `title`, else the first `#` heading, else the file name.
- **Markdown:** CommonMark with tables, strikethrough and autolinks. Raw HTML is shown as text, like on the doc
  service.
- **Links:** links to `.md` pages lead to the rendered pages, also with a `#fragment`. Links to pages that only exist
  on the doc service (generated chapters) are not checked and lead nowhere in the preview.
- **Diagrams:** fenced code blocks are rendered in the browser, without network access:

  | Fence                           | Rendered with                                   |
  |---------------------------------|-------------------------------------------------|
  | `mermaid`                       | Mermaid                                         |
  | `plantuml`, `puml`              | PlantUML (`@startuml`/`@enduml` may be omitted) |
  | `dot`, `graphviz`, `gv`         | Graphviz                                        |

  A PlantUML syntax error shows PlantUML's own error image. Other diagram errors show the message above the
  diagram source; the rest of the page still renders.
- **Images:** committed images (PNG, SVG, JPEG, GIF, WebP, AVIF), e.g. draw.io exports, are shown as they are.
- **Light and dark mode** follow the operating system.

Not supported: the doc service's own Markdown extensions (e.g. `:::details`), syntax highlighting of code blocks,
PlantUML `!include` of local files and of the PlantUML standard library (e.g. C4), and live rendering of `.drawio`
files.
