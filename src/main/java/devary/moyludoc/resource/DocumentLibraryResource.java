package devary.moyludoc.resource;

import devary.moyludoc.service.DocxHtmlRendererService;
import devary.moyludoc.service.DocxParsingService;
import devary.moyludoc.service.DocumentLibraryHtmlService;
import devary.moyludoc.service.DocumentLibraryService;
import devary.moyludoc.service.PdfPreviewService;
import devary.moyludoc.service.PresentationPreviewService;
import devary.moyludoc.service.SpreadsheetPreviewService;
import devary.moyludoc.service.TextPreviewService;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.RawString;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api/docx/library")
public class DocumentLibraryResource {

    @Inject DocumentLibraryService documentLibraryService;
    @Inject DocumentLibraryHtmlService documentLibraryHtmlService;
    @Inject DocxParsingService docxParsingService;
    @Inject DocxHtmlRendererService docxHtmlRendererService;
    @Inject SpreadsheetPreviewService spreadsheetPreviewService;
    @Inject PresentationPreviewService presentationPreviewService;
    @Inject PdfPreviewService pdfPreviewService;
    @Inject TextPreviewService textPreviewService;

    @CheckedTemplate(basePath = "")
    public static class Templates {
        public static native TemplateInstance browser(DocumentLibraryHtmlService.BrowserNode tree);
        public static native TemplateInstance previewPdf(String title, PdfPreviewService.PdfData data);
        public static native TemplateInstance previewText(String title, String fileTypeLabel, boolean isHtml, boolean isMarkdown, String body, RawString rawBody);
        public static native TemplateInstance previewSpreadsheet(String title, SpreadsheetPreviewService.SpreadsheetData data);
        public static native TemplateInstance previewPresentation(String title, PresentationPreviewService.PresentationData data);
    }

    @GET @Path("/tree") @Produces(MediaType.APPLICATION_JSON)
    public Uni<DocumentLibraryService.DocumentTreeNode> tree() { return Uni.createFrom().item(documentLibraryService::loadTree); }

    @GET @Path("/children") @Produces(MediaType.APPLICATION_JSON)
    public Uni<java.util.List<DocumentLibraryService.DocumentTreeNode>> children(@QueryParam("id") String id) {
        return Uni.createFrom().item(() -> documentLibraryService.loadChildren(id));
    }

    @GET @Path("/browser") @Produces(MediaType.TEXT_HTML)
    public Uni<String> browser() {
        return Uni.createFrom().item(() -> Templates.browser(documentLibraryHtmlService.toBrowserNode(documentLibraryService.loadTree())).render());
    }

    @GET @Path("/document/extract") @Produces(MediaType.APPLICATION_JSON)
    public Uni<Object> extract(@QueryParam("id") String id) {
        return Uni.createFrom().item(() -> extractStored(readDocument(id)));
    }

    @GET @Path("/document/preview") @Produces(MediaType.TEXT_HTML)
    public Uni<String> preview(@QueryParam("id") String id) {
        return Uni.createFrom().item(() -> previewStored(readDocument(id)));
    }

    @GET @Path("/document/meta") @Produces(MediaType.APPLICATION_JSON)
    public Uni<DocumentMeta> meta(@QueryParam("id") String id) {
        return Uni.createFrom().item(() -> {
            DocumentLibraryService.StoredDocument stored = readDocument(id);
            return new DocumentMeta(stored.name(), documentLibraryService.breadcrumbsById(id), stored.fileType(), stored.empty(), stored.sizeInBytes());
        });
    }

    @GET @Path("/document/download")
    public Uni<Response> download(@QueryParam("id") String id) {
        return Uni.createFrom().item(() -> {
            DocumentLibraryService.StoredDocument stored = readDocument(id);
            return Response.ok(stored.content())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + stored.name() + "\"")
                    .type(resolveMediaType(stored.fileType()))
                    .build();
        });
    }

    @POST @Path("/upload") @Consumes(MediaType.MULTIPART_FORM_DATA) @Produces(MediaType.APPLICATION_JSON)
    public Uni<UploadResponse> upload(@MultipartForm UploadForm form) {
        return Uni.createFrom().item(() -> {
            if (form == null || form.file == null) throw new WebApplicationException("A file is required", Response.Status.BAD_REQUEST);
            String targetFolder = form.folderId == null || form.folderId.isBlank() ? "" : form.folderId;
            try {
                java.nio.file.Path libraryRoot = java.nio.file.Path.of("docs-library").toAbsolutePath().normalize();
                java.nio.file.Path folder = targetFolder.isBlank()
                        ? libraryRoot
                        : libraryRoot.resolve(java.nio.file.Path.of(new String(java.util.Base64.getUrlDecoder().decode(targetFolder)))).normalize();
                Files.createDirectories(folder);
                java.nio.file.Path destination = folder.resolve(form.file.fileName()).normalize();
                if (!destination.startsWith(libraryRoot)) throw new WebApplicationException("Invalid target folder", Response.Status.BAD_REQUEST);
                Files.copy(form.file.uploadedFile(), destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                String id = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(libraryRoot.relativize(destination).toString().getBytes());
                return new UploadResponse(destination.getFileName().toString(), id);
            } catch (IOException e) {
                throw new WebApplicationException("Failed to store uploaded file", e);
            }
        });
    }

    private Object extractStored(DocumentLibraryService.StoredDocument stored) {
        if (stored.empty()) return new EmptyDocumentResponse(stored.name(), stored.fileType(), stored.sizeInBytes(), true);
        try {
            return switch (stored.fileType()) {
                case "docx" -> docxParsingService.parse(stored.content());
                case "xlsx" -> spreadsheetPreviewService.parse(stored.content());
                case "pptx" -> presentationPreviewService.parse(stored.content());
                case "pdf" -> pdfPreviewService.parse(stored.content());
                case "txt", "html", "md" -> textPreviewService.parse(stored.content(), stored.fileType());
                default -> throw new WebApplicationException("Unsupported file type", Response.Status.BAD_REQUEST);
            };
        } catch (Exception e) {
            return new DocumentErrorResponse(stored.name(), stored.fileType(), "extract", true,
                    "There was an error while parsing the document. Please contact Fakher Hammami.");
        }
    }

    private String previewStored(DocumentLibraryService.StoredDocument stored) {
        if (stored.empty()) return renderEmptyDocument(stored);
        try {
            return switch (stored.fileType()) {
                case "docx" -> docxHtmlRendererService.renderDocument(docxParsingService.parse(stored.content()), stored.name());
                case "xlsx" -> Templates.previewSpreadsheet(stored.name(), spreadsheetPreviewService.parse(stored.content())).render();
                case "pptx" -> Templates.previewPresentation(stored.name(), presentationPreviewService.parse(stored.content())).render();
                case "pdf" -> Templates.previewPdf(stored.name(), pdfPreviewService.parse(stored.content())).render();
                case "txt", "html", "md" -> {
                    TextPreviewService.TextData data = textPreviewService.parse(stored.content(), stored.fileType());
                    String renderedBody = textPreviewService.renderBody(data);
                    yield Templates.previewText(
                            stored.name(),
                            stored.fileType().toUpperCase(),
                            "html".equals(stored.fileType()),
                            "md".equals(stored.fileType()),
                            data.text(),
                            new RawString(renderedBody)).render();
                }
                default -> throw new WebApplicationException("Unsupported file type", Response.Status.BAD_REQUEST);
            };
        } catch (Exception e) {
            return renderPreviewError(stored, e);
        }
    }

    private DocumentLibraryService.StoredDocument readDocument(String id) {
        if (id == null || id.isBlank()) throw new WebApplicationException("Document id is required", Response.Status.BAD_REQUEST);
        try { return documentLibraryService.loadDocument(id); } catch (IllegalArgumentException e) { throw new WebApplicationException(e.getMessage(), Response.Status.NOT_FOUND); }
    }

    private String renderEmptyDocument(DocumentLibraryService.StoredDocument stored) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><title>"
                + escapeHtml(stored.name()) + "</title><style>body{font-family:Inter,Arial,sans-serif;background:#f8fafc;color:#0f172a;padding:32px}.box{max-width:720px;margin:40px auto;background:#fff;border-radius:16px;padding:32px;box-shadow:0 10px 30px rgba(0,0,0,.08)}.meta{color:#64748b}</style></head><body><div class=\"box\"><h1>"
                + escapeHtml(stored.name()) + "</h1><p class=\"meta\">" + escapeHtml(stored.fileType().toUpperCase()) + " file • empty document</p><p>There is nothing to preview because this file is empty.</p></div></body></html>";
    }

    private String renderPreviewError(DocumentLibraryService.StoredDocument stored, Exception e) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><title>Preview error</title><style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#7f1d1d;font-family:Inter,Arial,sans-serif;color:#fff;padding:24px}.error-box{max-width:760px;width:100%;background:#b91c1c;border:2px solid rgba(255,255,255,.28);border-radius:20px;padding:36px;text-align:center;box-shadow:0 20px 50px rgba(0,0,0,.35)}.icon{font-size:56px;line-height:1;margin-bottom:14px}.title{font-size:28px;font-weight:800;margin:0 0 10px}.subtitle{font-size:16px;opacity:.95;margin:0 0 14px}.meta{font-size:14px;opacity:.88;margin:6px 0}.help{margin-top:18px;font-size:15px;font-weight:700}</style></head><body><div class=\"error-box\"><div class=\"icon\">⚠️</div><h1 class=\"title\">Error while parsing the document</h1><p class=\"subtitle\">The document could not be rendered or extracted for preview.</p><p class=\"meta\">File: "
                + escapeHtml(stored.name()) + "</p><p class=\"meta\">Type: " + escapeHtml(stored.fileType().toUpperCase())
                + "</p><p class=\"meta\">Status: parsing/rendering failed</p><p class=\"help\">Please contact Fakher Hammami.</p></div></body></html>";
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

    private String resolveMediaType(String fileType) {
        return switch (fileType) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "pdf" -> "application/pdf";
            case "txt" -> MediaType.TEXT_PLAIN;
            case "html" -> MediaType.TEXT_HTML;
            case "md" -> "text/markdown";
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    public static class UploadForm {
        @RestForm("file") public FileUpload file;
        @RestForm("folderId") public String folderId;
    }

    public record EmptyDocumentResponse(String name, String fileType, long sizeInBytes, boolean empty) {}
    public record DocumentErrorResponse(String name, String fileType, String operation, boolean error, String message) {}
    public record DocumentMeta(String name, String breadcrumbs, String fileType, boolean empty, long sizeInBytes) {}
    public record UploadResponse(String name, String id) {}
}
