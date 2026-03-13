package devary.moyludoc.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;

@ApplicationScoped
public class TextPreviewService {

    public TextData parse(byte[] content, String fileType) {
        String text = new String(content, StandardCharsets.UTF_8);
        return new TextData(fileType, text);
    }

    public String renderHtml(TextData data, String title) {
        String body = switch (data.fileType()) {
            case "html" -> data.text();
            case "md" -> renderMarkdown(data.text());
            default -> "<pre>" + escape(data.text()) + "</pre>";
        };
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>" + escape(title) + "</title>"
                + "<style>body{font-family:Inter,Arial,sans-serif;background:#f8fafc;color:#0f172a;padding:32px}.page{max-width:900px;margin:0 auto;background:#fff;padding:32px;border-radius:16px;box-shadow:0 10px 30px rgba(0,0,0,.08)}pre{white-space:pre-wrap;word-break:break-word}.meta{color:#64748b}code{background:#eef2ff;padding:2px 6px;border-radius:6px}</style></head><body><div class=\"page\"><h1>"
                + escape(title) + "</h1><div class=\"meta\">" + escape(data.fileType().toUpperCase()) + " preview</div>" + body + "</div></body></html>";
    }

    private String renderMarkdown(String markdown) {
        StringBuilder html = new StringBuilder();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("### ")) html.append("<h3>").append(escape(line.substring(4))).append("</h3>");
            else if (line.startsWith("## ")) html.append("<h2>").append(escape(line.substring(3))).append("</h2>");
            else if (line.startsWith("# ")) html.append("<h1>").append(escape(line.substring(2))).append("</h1>");
            else if (line.startsWith("- ")) html.append("<li>").append(escape(line.substring(2))).append("</li>");
            else if (line.isBlank()) html.append("");
            else html.append("<p>").append(escape(line)).append("</p>");
        }
        return html.toString().replaceAll("(?s)(<li>.*?</li>)", "<ul>$1</ul>");
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public record TextData(String fileType, String text) {}
}
