package devary.moyludoc.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class DocumentLibraryHtmlService {

    public String renderBrowserPage(DocumentLibraryService.DocumentTreeNode tree) {
        String template = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Moyludoc Library</title>
                    <style>
                        body { margin: 0; font-family: Inter, Arial, sans-serif; background: #f3f4f6; color: #111827; }
                        .layout { display: grid; grid-template-columns: 340px 1fr; height: 100vh; }
                        .sidebar { background: #111827; color: #f9fafb; padding: 20px; overflow: auto; border-right: 1px solid #1f2937; }
                        .content { padding: 0; overflow: auto; background: #e5e7eb; }
                        .sidebar h1 { font-size: 20px; margin: 0 0 8px; }
                        .muted { font-size: 13px; color: #9ca3af; margin-bottom: 16px; }
                        .search { width: 100%; box-sizing: border-box; margin-bottom: 12px; padding: 10px 12px; border-radius: 10px; border: 1px solid #374151; background: #1f2937; color: #fff; }
                        .toolbar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
                        .toolbar button { border: 0; background: #2563eb; color: #fff; padding: 8px 12px; border-radius: 8px; cursor: pointer; }
                        .toolbar button.secondary { background: #374151; }
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
                        .tree-node.hidden { display: none; }
                        .viewer-header { padding: 14px 18px; background: #fff; border-bottom: 1px solid #d1d5db; display: flex; gap: 10px; align-items: center; position: sticky; top: 0; z-index: 10; }
                        .viewer { height: calc(100vh - 58px); width: 100%; border: 0; background: #fff; }
                    </style>
                </head>
                <body>
                    <div class="layout">
                        <aside class="sidebar">
                            <h1>Moyludoc Library</h1>
                            <div class="muted">Click a document to render it. Tree refreshes automatically every 15s.</div>
                            <input id="searchInput" class="search" type="search" placeholder="Search documents or folders...">
                            <div class="toolbar">
                                <button onclick="reloadTree(false)">Reload tree</button>
                                <button class="secondary" onclick="expandAll()">Expand all</button>
                                <button class="secondary" onclick="collapseAll()">Collapse all</button>
                            </div>
                            <ul class="tree tree-root">__TREE_HTML__</ul>
                        </aside>
                        <main class="content">
                            <div class="viewer-header">
                                <span id="currentDoc">No document selected</span>
                            </div>
                            <iframe id="viewer" class="viewer" title="Document preview"></iframe>
                        </main>
                    </div>
                    <script>
                        const REFRESH_MS = 15000;
                        let selectedDocId = null;

                        function bindTree() {
                            document.querySelectorAll('.doc-link').forEach(link => {
                                link.addEventListener('click', e => {
                                    e.preventDefault();
                                    openDocument(link.dataset.id, link.dataset.name);
                                });
                            });
                            document.querySelectorAll('.folder-row').forEach(row => {
                                row.addEventListener('click', () => toggleFolder(row.closest('.tree-node')));
                            });
                        }

                        function openDocument(id, name) {
                            selectedDocId = id;
                            document.querySelectorAll('.doc-link').forEach(x => x.classList.remove('active'));
                            const active = document.querySelector('.doc-link[data-id="' + cssEscape(id) + '"]');
                            if (active) {
                                active.classList.add('active');
                            }
                            document.getElementById('currentDoc').textContent = name;
                            document.getElementById('viewer').src = '/api/docx/library/document/preview?id=' + encodeURIComponent(id);
                        }

                        function toggleFolder(node) {
                            if (!node) return;
                            node.classList.toggle('collapsed');
                            const icon = node.querySelector(':scope > .folder-row .folder-toggle');
                            if (icon) {
                                icon.textContent = node.classList.contains('collapsed') ? '▸' : '▾';
                            }
                        }

                        function expandAll() {
                            document.querySelectorAll('.tree-node.folder').forEach(node => {
                                node.classList.remove('collapsed');
                                const icon = node.querySelector(':scope > .folder-row .folder-toggle');
                                if (icon) icon.textContent = '▾';
                            });
                        }

                        function collapseAll() {
                            document.querySelectorAll('.tree-node.folder').forEach(node => {
                                node.classList.add('collapsed');
                                const icon = node.querySelector(':scope > .folder-row .folder-toggle');
                                if (icon) icon.textContent = '▸';
                            });
                        }

                        function filterTree(term) {
                            const query = term.trim().toLowerCase();
                            document.querySelectorAll('.tree-node').forEach(node => node.classList.remove('hidden'));
                            if (!query) return;
                            filterNode(document.querySelector('.tree-root'), query);
                        }

                        function filterNode(container, query) {
                            let anyVisible = false;
                            container.querySelectorAll(':scope > .tree-node').forEach(node => {
                                let match = false;
                                const doc = node.querySelector(':scope > .doc-link');
                                const folderLabel = node.querySelector(':scope > .folder-row .folder-label');
                                if (doc) {
                                    match = doc.dataset.name.toLowerCase().includes(query);
                                    node.classList.toggle('hidden', !match);
                                } else if (folderLabel) {
                                    const selfMatch = folderLabel.textContent.toLowerCase().includes(query);
                                    const childList = node.querySelector(':scope > ul');
                                    const childMatch = childList ? filterNode(childList, query) : false;
                                    match = selfMatch || childMatch;
                                    node.classList.toggle('hidden', !match);
                                    if (match) {
                                        node.classList.remove('collapsed');
                                        const icon = node.querySelector(':scope > .folder-row .folder-toggle');
                                        if (icon) icon.textContent = '▾';
                                    }
                                }
                                anyVisible = anyVisible || match;
                            });
                            return anyVisible;
                        }

                        async function reloadTree(autoRefresh) {
                            const res = await fetch('/api/docx/library/tree');
                            const data = await res.json();
                            document.querySelector('.tree-root').innerHTML = renderNodes(data.children || []);
                            bindTree();
                            const currentQuery = document.getElementById('searchInput').value;
                            filterTree(currentQuery);
                            if (selectedDocId) {
                                const active = document.querySelector('.doc-link[data-id="' + cssEscape(selectedDocId) + '"]');
                                if (active) {
                                    openDocument(selectedDocId, active.dataset.name);
                                } else if (!autoRefresh) {
                                    document.getElementById('currentDoc').textContent = 'Selected document no longer exists';
                                    document.getElementById('viewer').src = 'about:blank';
                                }
                            }
                            if (!autoRefresh) {
                                document.getElementById('currentDoc').textContent = selectedDocId ? document.getElementById('currentDoc').textContent : 'Tree reloaded';
                            }
                        }

                        function renderNodes(nodes) {
                            return nodes.map(node => {
                                if (node.document) {
                                    return '<li class="tree-node document"><a href="#" class="doc-link" data-id="' + escapeHtml(node.id)
                                        + '" data-name="' + escapeHtml(node.name) + '">' + escapeHtml(node.name) + '</a></li>';
                                }
                                return '<li class="tree-node folder"><div class="folder-row"><span class="folder-toggle">▾</span><span class="folder-label">📁 '
                                    + escapeHtml(node.name) + '</span></div><ul>' + renderNodes(node.children || []) + '</ul></li>';
                            }).join('');
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
                        bindTree();
                        setInterval(() => reloadTree(true).catch(() => {}), REFRESH_MS);
                    </script>
                </body>
                </html>
                """;

        return template.replace("__TREE_HTML__", renderTree(tree.children()));
    }

    private String renderTree(List<DocumentLibraryService.DocumentTreeNode> nodes) {
        StringBuilder html = new StringBuilder();
        for (DocumentLibraryService.DocumentTreeNode node : nodes) {
            if (node.document()) {
                html.append("<li class=\"tree-node document\"><a href=\"#\" class=\"doc-link\" data-id=\"")
                        .append(escapeHtml(node.id()))
                        .append("\" data-name=\"")
                        .append(escapeHtml(node.name()))
                        .append("\">")
                        .append(escapeHtml(node.name()))
                        .append("</a></li>");
            } else {
                html.append("<li class=\"tree-node folder\"><div class=\"folder-row\"><span class=\"folder-toggle\">▾</span><span class=\"folder-label\">📁 ")
                        .append(escapeHtml(node.name()))
                        .append("</span></div><ul>")
                        .append(renderTree(node.children()))
                        .append("</ul></li>");
            }
        }
        return html.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
