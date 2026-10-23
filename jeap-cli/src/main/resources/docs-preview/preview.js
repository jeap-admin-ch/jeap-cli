// Renders the diagram placeholders written by the jEAP CLI docs preview: Mermaid with mermaid.js, PlantUML with
// @plantuml/core and Graphviz with Viz.js, all bundled with the CLI (no CDN). Only what a page needs is loaded.
const assets = document.querySelector('meta[name="preview-assets"]').content;
const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;
// Previews are published with other people's pull request content: sanitize the SVGs before inserting them
const SVG_PROFILE = {USE_PROFILES: {svg: true, svgFilters: true}};

function loadScript(src) {
    return new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = src;
        script.onload = resolve;
        script.onerror = () => reject(new Error(`Cannot load ${src}`));
        document.head.append(script);
    });
}

function showError(container, source, message) {
    const box = document.createElement('div');
    box.className = 'diagram-error';
    box.textContent = `Diagram error: ${message}`;
    const pre = document.createElement('pre');
    pre.textContent = source;
    container.replaceChildren(box, pre);
    container.classList.add('failed');
}

function insertSvg(container, svg) {
    container.innerHTML = window.DOMPurify.sanitize(svg, SVG_PROFILE);
}

async function renderMermaid() {
    const blocks = [...document.querySelectorAll('pre.mermaid')];
    if (!blocks.length) return;
    await loadScript(`${assets}vendor/mermaid.min.js`);
    window.mermaid.initialize({startOnLoad: false, theme: dark ? 'dark' : 'default', securityLevel: 'strict'});
    for (const block of blocks) {
        const source = block.textContent;
        const container = document.createElement('div');
        container.className = 'diagram mermaid-diagram';
        block.replaceWith(container);
        container.append(block);
        try {
            await window.mermaid.run({nodes: [block]});
        } catch (e) {
            showError(container, source, e.message ?? String(e));
        }
    }
}

async function renderGraphviz() {
    const blocks = [...document.querySelectorAll('div.diagram.graphviz')];
    if (!blocks.length) return;
    const viz = await window.Viz.instance();
    for (const block of blocks) {
        try {
            insertSvg(block, viz.renderString(block.dataset.source, {format: 'svg'}));
        } catch (e) {
            showError(block, block.dataset.source, e.message ?? String(e));
        }
    }
}

async function renderPlantUml() {
    const blocks = [...document.querySelectorAll('div.diagram.plantuml')];
    if (!blocks.length) return;
    if (blocks.some((block) => /^\s*!theme\b/m.test(block.dataset.source))) {
        // themes.js registers globalThis.PLANTUML_THEMES; the engine would look for it next to the page otherwise
        await loadScript(`${assets}vendor/themes.js`).catch((e) => console.error(e));
    }
    const {renderToString} = await import(new URL(`${assets}vendor/plantuml.js`, location.href).href);
    for (const block of blocks) {
        let lines = block.dataset.source.replace(/\n$/, '').split('\n');
        if (!lines.some((line) => line.trim().startsWith('@start'))) {
            lines = ['@startuml', ...lines, '@enduml'];
        }
        // Syntax errors arrive through onSuccess as PlantUML's own error image; onError means the engine failed
        await new Promise((resolve) => renderToString(lines,
            (svg) => {
                insertSvg(block, svg);
                resolve();
            },
            (error) => {
                showError(block, block.dataset.source, error);
                resolve();
            },
            {dark}));
    }
}

async function renderDiagrams() {
    if (document.querySelector('div.diagram.plantuml, div.diagram.graphviz')) {
        await Promise.all([
            loadScript(`${assets}vendor/viz-global.js`),
            loadScript(`${assets}vendor/purify.min.js`),
        ]);
    }
    await Promise.all([
        renderMermaid().catch((e) => console.error(e)),
        renderGraphviz().catch((e) => console.error(e)),
        renderPlantUml().catch((e) => console.error(e)),
    ]);
}

renderDiagrams();
