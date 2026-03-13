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
                        .layout { display: grid; grid-template-columns: 320px 1fr; height: 100vh; }
                        .sidebar { background: #111827; color: #f9fafb; padding: 20px; overflow: auto; border-right: 1px solid #1f2937; }
                        .content { padding: 0; overflow: auto; background: #e5e7eb; }
                        .sidebar h1 { font-size: 20px; margin: 0 0 8px; }
                        .muted { font-size: 13px; color: #9ca3af; margin-bottom: 16px; }
                        .tree, .tree ul { list-style: none; padding-left: 16px; margin: 0; }
                        .tree-root { padding-left: 0; }
                        .folder { margin: 8px 0 4px; color: #d1d5db; font-weight: 600; }
                        .doc-link { display: block; color: #93c5fd; text-decoration: none; padding: 6px 8px; border-radius: 8px; margin: 2px 0; font-size: 14px; }
                        .doc-link:hover, .doc-link.active { background: #1f2937; color: #fff; }
                        .viewer-header { padding: 14px 18px; background: #fff; border-bottom: 1px solid #d1d5db; display: flex; gap: 10px; align-items: center; position: sticky; top: 0; z-index: 10; }
                        .viewer { height: calc(100vh - 58px); width: 100%; border: 0; background: #fff; }
                        button { border: 0; background: #2563eb; color: #fff; padding: 8px 12px; border-radius: 8px; cursor: pointer; }
                    </style>
                </head>
                <body>
                    <div class="layout">
                        <aside class="sidebar">
                            <h1>Moyludoc Library</h1>
                            <div class="muted">Click a document to render it.</div>
                            <ul class="tree tree-root">__TREE_HTML__</ul>
                        </aside>
                        <main class="content">
                            <div class="viewer-header">
                                <button onclick="reloadTree()">Reload tree</button>
                                <span id="currentDoc">No document selected</span>
                            </div>
                            <iframe id="viewer" class="viewer" title="Document preview"></iframe>
                        </main>
                    </div>
                    <script>
                        function bindLinks() {
                            document.querySelectorAll('.doc-link').forEach(link => {
                                link.addEventListener('click', e => {
                                    e.preventDefault();
                                    document.querySelectorAll('.doc-link').forEach(x => x.classList.remove('active'));
                                    link.classList.add('active');
                                    document.getElementById('currentDoc').textContent = link.dataset.name;
                                    document.getElementById('viewer').src = '/api/docx/library/document/preview?id=' + encodeURIComponent(link.dataset.id);
                                });
                            });
                        }

                        async function reloadTree() {
                            const res = await fetch('/api/docx/library/tree');
                            const data = await res.json();
                            document.querySelector('.tree-root').innerHTML = renderNodes(data.children || []);
                            bindLinks();
                            document.getElementById('viewer').src = 'about:blank';
                            document.getElementById('currentDoc').textContent = 'Tree reloaded';
                        }

                        function renderNodes(nodes) {
                            return nodes.map(node => {
                                if (node.document) {
                                    return '<li><a href="#" class="doc-link" data-id="' + escapeHtml(node.id)
                                        + '" data-name="' + escapeHtml(node.name) + '">' + escapeHtml(node.name) + '</a></li>';
                                }
                                return '<li><div class="folder">📁 ' + escapeHtml(node.name)
                                    + '</div><ul>' + renderNodes(node.children || []) + '</ul></li>';
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

                        bindLinks();
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
                html.append("<li><a href=\"#\" class=\"doc-link\" data-id=\"")
                        .append(escapeHtml(node.id()))
                        .append("\" data-name=\"")
                        .append(escapeHtml(node.name()))
                        .append("\">")
                        .append(escapeHtml(node.name()))
                        .append("</a></li>");
            } else {
                html.append("<li><div class=\"folder\">📁 ")
                        .append(escapeHtml(node.name()))
                        .append("</div><ul>")
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
