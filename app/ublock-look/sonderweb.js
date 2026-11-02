// Tells SonderWeb how tall the panel is, so its sheet fits it (read from the page title).
(() => {
    const panes = document.getElementById('panes');
    if (!panes) return;
    let last = 0;
    const report = () => {
        const h = Math.ceil(panes.getBoundingClientRect().height) + 8;
        if (h > 40 && (h !== last || !document.title.startsWith('sw-h:'))) {
            last = h;
            document.title = 'sw-h:' + h;
        }
    };
    new ResizeObserver(report).observe(panes);
    new MutationObserver(report).observe(document.body, { attributes: true, attributeFilter: ['class', 'data-more'] });
    // uBlock names the page once loaded; put the height back.
    new MutationObserver(report).observe(document.head, { childList: true, subtree: true, characterData: true });
    report();
})();
