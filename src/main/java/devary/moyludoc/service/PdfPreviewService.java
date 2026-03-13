package devary.moyludoc.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

@ApplicationScoped
public class PdfPreviewService {

    public PdfData parse(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return new PdfData(document.getNumberOfPages(), text == null ? "" : text.trim());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse PDF", e);
        }
    }

    public String renderHtml(PdfData data, String title) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>" + escape(title) + "</title>"
                + "<style>body{font-family:Inter,Arial,sans-serif;background:#f8fafc;color:#0f172a;padding:32px}.page{max-width:900px;margin:0 auto;background:#fff;padding:32px;border-radius:16px;box-shadow:0 10px 30px rgba(0,0,0,.08)}pre{white-space:pre-wrap;word-break:break-word;background:#f8fafc;padding:16px;border-radius:12px;border:1px solid #e2e8f0}.meta{color:#64748b}</style></head><body><div class=\"page\"><h1>"
                + escape(title) + "</h1><div class=\"meta\">PDF preview • " + data.pageCount() + " pages</div><pre>" + escape(data.text()) + "</pre></div></body></html>";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public record PdfData(int pageCount, String text) {}
}
