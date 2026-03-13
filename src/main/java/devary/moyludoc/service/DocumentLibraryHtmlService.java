package devary.moyludoc.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class DocumentLibraryHtmlService {

    public String renderBrowserPage(DocumentLibraryService.DocumentTreeNode tree) {
        String treeHtml = renderTree(tree.children());
        return "<!DOCTYPE html>"
                + "<html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>Moyludoc Library</title>"
                + "<style>"
                + "body{margin:0;font-family:Inter,Arial,sans-serif;background:#f3f4f6;color:#111827;}"
                + ".layout{display:grid;grid-template-columns:320px 1fr;height:100vh;}"
                + ".sidebar{background:#111827;color:#f9fafb;padding:20px;overflow:auto;border-right:1px solid #1f2937;}"
                + ".content{padding:0;overflow:auto;background:#e5e7eb;}"
                + ".sidebar h1{font-size:20px;margin:0 0 8px;}"
                + ".muted{font-size:13px;color:#9ca3af;margin-bottom:16px;}"
                + ".tree,.tree ul{list-style:none;padding-left:16px;margin:0;}"
                + ".tree-root{padding-left:0;}"
                + ".folder-row{display:flex;align-items:center;gap:8px;padding:6px 8px;border-radius:8px;color:#d1d5db;font-weight:600;cursor:pointer;user-select:none;}"
                + ".folder-row:hover{background:#1f2937;}"
                + ".folder-caret{display:inline-block;width:14px;color:#93c5fd;transition:transform .15s ease;}"
                + ".folder-node.collapsed>.folder-row .folder-caret{transform:rotate(0deg);}"
                + ".folder-node:not(.collapsed)>.folder-row .folder-caret{transform:rotate(90deg);}"
                + ".folder-children{display:block;}"
                + ".folder-node.collapsed>.folder-children{display:none;}"
                + ".doc-link{display:block;color:#93c5fd;text-decoration:none;padding:6px 8px;border-radius:8px;margin:2px 0;font-size:14px;}"
                + ".doc-link:hover,.doc-link.active{background:#1f2937;color:#fff;}"
                + ".doc-link.empty-file{color:#fca5a5;}"
                + ".doc-path{display:block;color:#9ca3af;font-size:12px;margin-top:2px;}"
                + ".viewer-header{padding:14px 18px;background:#fff;border-bottom:1px solid #d1d5db;display:flex;gap:10px;align-items:center;justify-content:space-between;position:sticky;top:0;z-index:10;}"
                + ".viewer-meta{display:flex;flex-direction:column;gap:4px;}"
                + ".breadcrumbs{color:#64748b;font-size:13px;}"
                + ".viewer-actions a{border:0;background:#2563eb;color:#fff;padding:8px 12px;border-radius:8px;cursor:pointer;text-decoration:none;font-size:14px;}"
                + ".viewer{height:calc(100vh - 58px);width:100%;border:0;background:#fff;}"
                + "</style></head><body>"
                + "<div class=\"layout\">"
                + "<aside class=\"sidebar\"><h1>Moyludoc Library</h1><div class=\"muted\">Dynamic collapsible tree restored.</div>"
                + "<ul class=\"tree tree-root\">" + treeHtml + "</ul></aside>"
                + "<main class=\"content\">"
                + "<div class=\"viewer-header\"><div class=\"viewer-meta\"><strong id=\"currentDoc\">No document selected</strong><span id=\"breadcrumbs\" class=\"breadcrumbs\">—</span></div><div class=\"viewer-actions\"><a id=\"downloadBtn\" href=\"#\" onclick=\"return false;\">Download original</a></div></div>"
                + "<iframe id=\"viewer\" class=\"viewer\" title=\"Document preview\"></iframe>"
                + "</main></div>"
                + "<script>"
                + "document.querySelectorAll('.doc-link').forEach(link=>{link.addEventListener('click',async e=>{e.preventDefault();document.querySelectorAll('.doc-link').forEach(x=>x.classList.remove('active'));link.classList.add('active');const id=link.dataset.id;const meta=await fetch('/api/docx/library/document/meta?id='+encodeURIComponent(id)).then(r=>r.json());document.getElementById('currentDoc').textContent=meta.name;document.getElementById('breadcrumbs').textContent=meta.breadcrumbs||'—';document.getElementById('downloadBtn').href='/api/docx/library/document/download?id='+encodeURIComponent(id);document.getElementById('viewer').src='/api/docx/library/document/preview?id='+encodeURIComponent(id);});});"
                + "document.querySelectorAll('.folder-row').forEach(row=>{row.addEventListener('click',()=>{const node=row.closest('.folder-node');if(node){node.classList.toggle('collapsed');}});});"
                + "</script></body></html>";
    }

    private String renderTree(List<DocumentLibraryService.DocumentTreeNode> nodes) {
        StringBuilder html = new StringBuilder();
        for (DocumentLibraryService.DocumentTreeNode node : nodes) {
            if (node.document()) {
                html.append("<li><a href=\"#\" class=\"doc-link")
                        .append(node.empty() ? " empty-file" : "")
                        .append("\" data-id=\"")
                        .append(escapeHtml(node.id()))
                        .append("\" data-name=\"")
                        .append(escapeHtml(node.name()))
                        .append("\">")
                        .append(iconFor(node.fileType()))
                        .append(" ")
                        .append(escapeHtml(node.name()))
                        .append(node.empty() ? " (empty)" : "")
                        .append("<span class=\"doc-path\">")
                        .append(escapeHtml(node.relativePath()))
                        .append("</span></a></li>");
            } else {
                html.append("<li class=\"folder-node collapsed\"><div class=\"folder-row\"><span class=\"folder-caret\">▶</span><span>📁 ")
                        .append(escapeHtml(node.name()))
                        .append("</span></div><ul class=\"folder-children\">")
                        .append(renderTree(node.children()))
                        .append("</ul></li>");
            }
        }
        return html.toString();
    }

    private String iconFor(String type) {
        if (type == null) {
            return "📄";
        }
        return switch (type) {
            case "xlsx" -> "📊";
            case "pptx" -> "📽️";
            case "pdf" -> "📕";
            case "html" -> "🌐";
            case "md" -> "📝";
            case "txt" -> "📄";
            default -> "📄";
        };
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
