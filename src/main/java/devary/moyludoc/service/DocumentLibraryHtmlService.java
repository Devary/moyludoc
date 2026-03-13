package devary.moyludoc.service;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DocumentLibraryHtmlService {

    public String renderBrowserPage(DocumentLibraryService.DocumentTreeNode tree) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Moyludoc Library</title>
                    <style>
                        body { margin: 0; font-family: Inter, Arial, sans-serif; background: #f3f4f6; color: #111827; }
                        .layout { display: grid; grid-template-columns: 360px 1fr; height: 100vh; }
                        .sidebar { background: #111827; color: #f9fafb; padding: 20px; overflow: auto; border-right: 1px solid #1f2937; }
                        .content { padding: 0; overflow: auto; background: #e5e7eb; }
                        .sidebar h1 { font-size: 20px; margin: 0 0 8px; }
                        .muted { font-size: 13px; color: #9ca3af; margin-bottom: 16px; }
                        .search { width: 100%; box-sizing: border-box; margin-bottom: 12px; padding: 10px 12px; border-radius: 10px; border: 1px solid #374151; background: #1f2937; color: #fff; }
                        .toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
                        .toolbar button, .toolbar a { border: 0; background: #2563eb; color: #fff; padding: 8px 12px; border-radius: 8px; cursor: pointer; text-decoration: none; font-size: 14px; }
                        .toolbar .secondary { background: #374151; }
                        .tree, .tree ul { list-style: none; padding-left: 16px; margin: 0; }
                        .tree-root { padding-left: 0; }
                        .tree-node { margin: 3px 0; }
                        .folder-row { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 8px; color: #d1d5db; cursor: pointer; user-select: none; }
                        .folder-row:hover { background: #1f2937; }
                        .folder-toggle { width: 14px; display: inline-block; color: #9ca3af; }
                        .folder-label { font-weight: 600; }
                        .tree-node.collapsed > ul { display: none; }
                        .doc-link { display: block; color: #93c5fd; text-decoration: none; padding: 6px 8px; border-radius: 8px; margin: 2px 0; font-size: 14px; }
                        .doc-link:hover, .doc-link.active { background: #1f2937; color: #fff; }
                        .doc-link.empty-file { color: #fca5a5; }
                        .tree-node.hidden { display: none; }
                        .viewer-header { padding: 14px 18px; background: #fff; border-bottom: 1px solid #d1d5db; display: flex; gap: 10px; align-items: center; justify-content: space-between; position: sticky; top: 0; z-index: 10; }
                        .viewer-meta { display: flex; flex-direction: column; gap: 4px; }
                        .breadcrumbs { color: #64748b; font-size: 13px; }
                        .viewer-actions { display: flex; gap: 8px; }
                        .viewer { height: calc(100vh - 74px); width: 100%; border: 0; background: #fff; }
                    </style>
                </head>
                <body>
                    <div class="layout">
                        <aside class="sidebar">
                            <h1>Moyludoc Library</h1>
                            <div class="muted">DOCX, XLSX, PPTX • empty files are shown • folders lazy-load when expanded.</div>
                            <input id="searchInput" class="search" type="search" placeholder="Search documents or folders...">
                            <div class="toolbar">
                                <button onclick="reloadRoot(false)">Reload tree</button>
                                <button class="secondary" onclick="expandAllVisible()">Expand all</button>
                                <button class="secondary" onclick="collapseAllVisible()">Collapse all</button>
                            </div>
                            <ul id="treeRoot" class="tree tree-root"></ul>
                        </aside>
                        <main class="content">
                            <div class="viewer-header">
                                <div class="viewer-meta">
                                    <strong id="currentDoc">No document selected</strong>
                                    <span id="breadcrumbs" class="breadcrumbs">—</span>
                                </div>
                                <div class="viewer-actions">
                                    <a id="downloadBtn" href="#" class="secondary" onclick="return false;">Download original</a>
                                </div>
                            </div>
                            <iframe id="viewer" class="viewer" title="Document preview"></iframe>
                        </main>
                    </div>
                    <script>
                        let selectedDocId = null;
                        const refreshMs = 15000;

                        async function fetchChildren(id) {
                            const url = '/api/docx/library/children' + (id ? ('?id=' + encodeURIComponent(id)) : '');
                            const response = await fetch(url);
                            return await response.json();
                        }

                        async function reloadRoot(auto) {
                            const nodes = await fetchChildren('');
                            document.getElementById('treeRoot').innerHTML = renderNodes(nodes);
                            bindTree();
                            filterTree(document.getElementById('searchInput').value);
                            if (selectedDocId) {
                                const active = document.querySelector('.doc-link[data-id="' + cssEscape(selectedDocId) + '"]');
                                if (active) {
                                    await ensurePathExpanded(active.closest('.tree-node'));
                                    active.classList.add('active');
                                } else if (!auto) {
                                    document.getElementById('currentDoc').textContent = 'Selected document not found';
                                    document.getElementById('breadcrumbs').textContent = '—';
                                    document.getElementById('viewer').src = 'about:blank';
                                }
                            }
                        }

                        function renderNodes(nodes) {
                            return nodes.map(node => {
                                if (node.document) {
                                    const classes = 'doc-link' + (node.empty ? ' empty-file' : '');
                                    const suffix = node.empty ? ' (empty)' : '';
                                    const icon = node.fileType === 'xlsx' ? '📊' : (node.fileType === 'pptx' ? '📽️' : '📄');
                                    return '<li class="tree-node document"><a href="#" class="' + classes + '" data-id="' + escapeHtml(node.id)
                                        + '" data-name="' + escapeHtml(node.name) + '" data-file-type="' + escapeHtml(node.fileType)
                                        + '">' + icon + ' ' + escapeHtml(node.name + suffix) + '</a></li>';
                                }
                                return '<li class="tree-node folder collapsed" data-id="' + escapeHtml(node.id)
                                    + '" data-loaded="false"><div class="folder-row"><span class="folder-toggle">▸</span><span class="folder-label">📁 '
                                    + escapeHtml(node.name) + '</span></div><ul></ul></li>';
                            }).join('');
                        }

                        function bindTree() {
                            document.querySelectorAll('.doc-link').forEach(link => {
                                link.addEventListener('click', async e => {
                                    e.preventDefault();
                                    await openDocument(link.dataset.id, link.dataset.name);
                                });
                            });
                            document.querySelectorAll('.folder-row').forEach(row => {
                                row.addEventListener('click', async () => {
                                    await toggleFolder(row.closest('.tree-node.folder'));
                                });
                            });
                        }

                        async function toggleFolder(node) {
                            if (!node) return;
                            const collapsed = node.classList.contains('collapsed');
                            if (collapsed && node.dataset.loaded !== 'true') {
                                const children = await fetchChildren(node.dataset.id);
                                node.querySelector(':scope > ul').innerHTML = renderNodes(children);
                                node.dataset.loaded = 'true';
                                bindTree();
                            }
                            node.classList.toggle('collapsed');
                            const icon = node.querySelector(':scope > .folder-row .folder-toggle');
                            if (icon) icon.textContent = node.classList.contains('collapsed') ? '▸' : '▾';
                            filterTree(document.getElementById('searchInput').value);
                        }

                        async function openDocument(id, name) {
                            selectedDocId = id;
                            document.querySelectorAll('.doc-link').forEach(x => x.classList.remove('active'));
                            const active = document.querySelector('.doc-link[data-id="' + cssEscape(id) + '"]');
                            if (active) active.classList.add('active');
                            const meta = await fetch('/api/docx/library/document/meta?id=' + encodeURIComponent(id)).then(r => r.json());
                            document.getElementById('currentDoc').textContent = meta.name;
                            document.getElementById('breadcrumbs').textContent = meta.breadcrumbs || '—';
                            document.getElementById('downloadBtn').href = '/api/docx/library/document/download?id=' + encodeURIComponent(id);
                            document.getElementById('viewer').src = '/api/docx/library/document/preview?id=' + encodeURIComponent(id);
                        }

                        async function ensurePathExpanded(node) {
                            let current = node ? node.parentElement?.closest('.tree-node.folder') : null;
                            while (current) {
                                if (current.classList.contains('collapsed')) {
                                    await toggleFolder(current);
                                }
                                current = current.parentElement?.closest('.tree-node.folder');
                            }
                        }

                        async function expandAllVisible() {
                            const folders = Array.from(document.querySelectorAll('.tree-node.folder'));
                            for (const folder of folders) {
                                if (folder.classList.contains('hidden')) continue;
                                if (folder.dataset.loaded !== 'true') {
                                    const children = await fetchChildren(folder.dataset.id);
                                    folder.querySelector(':scope > ul').innerHTML = renderNodes(children);
                                    folder.dataset.loaded = 'true';
                                    bindTree();
                                }
                                folder.classList.remove('collapsed');
                                const icon = folder.querySelector(':scope > .folder-row .folder-toggle');
                                if (icon) icon.textContent = '▾';
                            }
                            filterTree(document.getElementById('searchInput').value);
                        }

                        function collapseAllVisible() {
                            document.querySelectorAll('.tree-node.folder').forEach(folder => {
                                if (folder.classList.contains('hidden')) return;
                                folder.classList.add('collapsed');
                                const icon = folder.querySelector(':scope > .folder-row .folder-toggle');
                                if (icon) icon.textContent = '▸';
                            });
                        }

                        function filterTree(term) {
                            const query = term.trim().toLowerCase();
                            if (!query) {
                                document.querySelectorAll('.tree-node').forEach(node => node.classList.remove('hidden'));
                                return true;
                            }
                            return filterContainer(document.getElementById('treeRoot'), query);
                        }

                        function filterContainer(container, query) {
                            let anyVisible = false;
                            container.querySelectorAll(':scope > .tree-node').forEach(node => {
                                let match = false;
                                const doc = node.querySelector(':scope > .doc-link');
                                const folder = node.querySelector(':scope > .folder-row .folder-label');
                                if (doc) {
                                    match = doc.dataset.name.toLowerCase().includes(query);
                                } else if (folder) {
                                    const selfMatch = folder.textContent.toLowerCase().includes(query);
                                    const childList = node.querySelector(':scope > ul');
                                    const childMatch = childList ? filterContainer(childList, query) : false;
                                    match = selfMatch || childMatch;
                                    if (match) {
                                        node.classList.remove('collapsed');
                                        const icon = node.querySelector(':scope > .folder-row .folder-toggle');
                                        if (icon) icon.textContent = '▾';
                                    }
                                }
                                node.classList.toggle('hidden', !match);
                                anyVisible = anyVisible || match;
                            });
                            return anyVisible;
                        }

                        function escapeHtml(value) {
                            return String(value ?? '')
                                .replaceAll('&', '&amp;')
                                .replaceAll('<', '&lt;')
                                .replaceAll('>', '&gt;')
                                .replaceAll('"', '&quot;')
                                .replaceAll("'", '&#39;');
                        }

                        function cssEscape(value) {
                            if (window.CSS && CSS.escape) return CSS.escape(value);
                            return String(value).replaceAll('"', '\\"');
                        }

                        document.getElementById('searchInput').addEventListener('input', e => filterTree(e.target.value));
                        reloadRoot(false);
                        setInterval(() => reloadRoot(true).catch(() => {}), refreshMs);
                    </script>
                </body>
                </html>
                """;
    }
}
