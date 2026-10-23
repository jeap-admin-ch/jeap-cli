const assets = document.querySelector('meta[name="preview-assets"]').content;
const dark = window.matchMedia('(prefers-color-scheme: dark)').matches;

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
            block.replaceChildren(viz.renderSVGElement(block.dataset.source));
        } catch (e) {
            showError(block, block.dataset.source, e.message ?? String(e));
        }
    }
}

async function renderPlantUml() {
    const blocks = [...document.querySelectorAll('div.diagram.plantuml')];
    if (!blocks.length) return;
    const {renderToString} = await import(new URL(`${assets}vendor/plantuml.js`, location.href).href);
    for (const block of blocks) {
        let lines = block.dataset.source.replace(/\n$/, '').split('\n');
        if (!lines.some((line) => line.trim().startsWith('@start'))) {
            lines = ['@startuml', ...lines, '@enduml'];
        }
        await new Promise((resolve) => renderToString(lines,
            (svg) => {
                block.innerHTML = svg;
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
    const needsViz = document.querySelector('div.diagram.plantuml, div.diagram.graphviz');
    if (needsViz) {
        await loadScript(`${assets}vendor/viz-global.js`);
    }
    await Promise.all([
        renderMermaid().catch((e) => console.error(e)),
        renderGraphviz().catch((e) => console.error(e)),
        renderPlantUml().catch((e) => console.error(e)),
    ]);
}

renderDiagrams();
