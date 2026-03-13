package devary.moyludoc.resource;

import devary.moyludoc.service.DocxHtmlRendererService;
import devary.moyludoc.service.DocxParsingService;
import devary.moyludoc.service.DocumentLibraryHtmlService;
import devary.moyludoc.service.DocumentLibraryService;
import devary.moyludoc.service.PresentationPreviewService;
import devary.moyludoc.service.SpreadsheetPreviewService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/docx/library")
public class DocumentLibraryResource {

    @Inject DocumentLibraryService documentLibraryService;
    @Inject DocumentLibraryHtmlService documentLibraryHtmlService;
    @Inject DocxParsingService docxParsingService;
    @Inject DocxHtmlRendererService docxHtmlRendererService;
    @Inject SpreadsheetPreviewService spreadsheetPreviewService;
    @Inject PresentationPreviewService presentationPreviewService;

    @GET
    @Path("/tree")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<DocumentLibraryService.DocumentTreeNode> tree() {
        return Uni.createFrom().item(documentLibraryService::loadTree);
    }

    @GET
    @Path("/children")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<java.util.List<DocumentLibraryService.DocumentTreeNode>> children(@QueryParam("id") String id) {
        return Uni.createFrom().item(() -> documentLibraryService.loadChildren(id));
    }

    @GET
    @Path("/browser")
    @Produces(MediaType.TEXT_HTML)
    public Uni<String> browser() {
        return Uni.createFrom().item(() -> documentLibraryHtmlService.renderBrowserPage(documentLibraryService.loadTree()));
    }

    @GET
    @Path("/document/extract")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<Object> extract(@QueryParam("id") String id) {
        return Uni.createFrom().item(() -> {
            DocumentLibraryService.StoredDocument stored = readDocument(id);
            if (stored.empty()) {
                return new EmptyDocumentResponse(stored.name(), stored.fileType(), stored.sizeInBytes(), true);
            }
            return switch (stored.fileType()) {
                case "docx" -> docxParsingService.parse(stored.content());
                case "xlsx" -> spreadsheetPreviewService.parse(stored.content());
                case "pptx" -> presentationPreviewService.parse(stored.content());
                default -> throw new WebApplicationException("Unsupported file type", Response.Status.BAD_REQUEST);
            };
        });
    }

    @GET
    @Path("/document/preview")
    @Produces(MediaType.TEXT_HTML)
    public Uni<String> preview(@QueryParam("id") String id) {
        return Uni.createFrom().item(() -> {
            DocumentLibraryService.StoredDocument stored = readDocument(id);
            if (stored.empty()) {
                return renderEmptyDocument(stored);
            }
            return switch (stored.fileType()) {
                case "docx" -> docxHtmlRendererService.renderDocument(docxParsingService.parse(stored.content()), stored.name());
                case "xlsx" -> spreadsheetPreviewService.renderHtml(spreadsheetPreviewService.parse(stored.content()), stored.name());
                case "pptx" -> presentationPreviewService.renderHtml(presentationPreviewService.parse(stored.content()), stored.name());
                default -> throw new WebApplicationException("Unsupported file type", Response.Status.BAD_REQUEST);
            };
        });
    }

    @GET
    @Path("/document/meta")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<DocumentMeta> meta(@QueryParam("id") String id) {
        return Uni.createFrom().item(() -> {
            DocumentLibraryService.StoredDocument stored = readDocument(id);
            return new DocumentMeta(
                    stored.name(),
                    documentLibraryService.breadcrumbsById(id),
                    stored.fileType(),
                    stored.empty(),
                    stored.sizeInBytes());
        });
    }

    @GET
    @Path("/document/download")
    public Uni<Response> download(@QueryParam("id") String id) {
        return Uni.createFrom().item(() -> {
            DocumentLibraryService.StoredDocument stored = readDocument(id);
            return Response.ok(stored.content())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + stored.name() + "\"")
                    .type(resolveMediaType(stored.fileType()))
                    .build();
        });
    }

    private DocumentLibraryService.StoredDocument readDocument(String id) {
        if (id == null || id.isBlank()) {
            throw new WebApplicationException("Document id is required", Response.Status.BAD_REQUEST);
        }
        try {
            return documentLibraryService.loadDocument(id);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.NOT_FOUND);
        }
    }

    private String renderEmptyDocument(DocumentLibraryService.StoredDocument stored) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><title>"
                + stored.name()
                + "</title><style>body{font-family:Inter,Arial,sans-serif;background:#f8fafc;color:#0f172a;padding:32px}.box{max-width:720px;margin:40px auto;background:#fff;border-radius:16px;padding:32px;box-shadow:0 10px 30px rgba(0,0,0,.08)}.meta{color:#64748b}</style></head><body><div class=\"box\"><h1>"
                + stored.name()
                + "</h1><p class=\"meta\">"
                + stored.fileType().toUpperCase()
                + " file • empty document</p><p>There is nothing to preview because this file is empty.</p></div></body></html>";
    }

    private String resolveMediaType(String fileType) {
        return switch (fileType) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    public record EmptyDocumentResponse(String name, String fileType, long sizeInBytes, boolean empty) {}
    public record DocumentMeta(String name, String breadcrumbs, String fileType, boolean empty, long sizeInBytes) {}
}
