(() => {
    if (new URLSearchParams(location.search).has('noreload')) return;
    const assets = document.querySelector('meta[name="preview-assets"]').content;
    const scrollKey = `docs-preview-scroll:${location.pathname}`;

    try {
        const saved = sessionStorage.getItem(scrollKey);
        if (saved !== null) {
            sessionStorage.removeItem(scrollKey);
            window.addEventListener('load', () => window.scrollTo(0, Number(saved)));
        }
    } catch (e) {
        // storage unavailable; reload without restoring the position
    }

    function reload() {
        try {
            sessionStorage.setItem(scrollKey, String(window.scrollY));
        } catch (e) {
            // ignore
        }
        location.reload();
    }

    let lostConnection = false;
    const events = new EventSource(`${assets}events`);
    events.onmessage = reload;
    events.onerror = () => {
        lostConnection = true;
    };
    events.onopen = () => {
        if (lostConnection) reload();
    };
})();
