package devary.moyludoc.resource;

import devary.moyludoc.service.DocxHtmlRendererService;
import devary.moyludoc.service.DocxParsingService;
import devary.moyludoc.service.DocumentLibraryHtmlService;
import devary.moyludoc.service.DocumentLibraryService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/docx/library")
public class DocumentLibraryResource {

    @Inject
    DocumentLibraryService documentLibraryService;

    @Inject
    DocumentLibraryHtmlService documentLibraryHtmlService;

    @Inject
    DocxParsingService docxParsingService;

    @Inject
    DocxHtmlRendererService docxHtmlRendererService;

    @GET
    @Path("/tree")
    @Produces(MediaType.APPLICATION_JSON)
    public Uni<DocumentLibraryService.DocumentTreeNode> tree() {
        return Uni.createFrom().item(documentLibraryService::loadTree);
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
    public Uni<DocxParsingService.ParsedDocx> extract(@QueryParam("id") String id) {
        return Uni.createFrom().item(() -> docxParsingService.parse(readDocument(id)));
    }

    @GET
    @Path("/document/preview")
    @Produces(MediaType.TEXT_HTML)
    public Uni<String> preview(@QueryParam("id") String id,
            @QueryParam("embedded") @DefaultValue("false") boolean embedded) {
        return Uni.createFrom().item(() -> {
            DocxParsingService.ParsedDocx parsed = docxParsingService.parse(readDocument(id));
            String html = docxHtmlRendererService.renderDocument(parsed, documentLibraryService.titleById(id));
            if (!embedded) {
                return html;
            }
            return extractBody(html);
        });
    }

    private byte[] readDocument(String id) {
        if (id == null || id.isBlank()) {
            throw new WebApplicationException("Document id is required", Response.Status.BAD_REQUEST);
        }
        try {
            return documentLibraryService.loadDocumentById(id);
        } catch (IllegalArgumentException e) {
            throw new WebApplicationException(e.getMessage(), Response.Status.NOT_FOUND);
        }
    }

    private String extractBody(String html) {
        int bodyStart = html.indexOf("<body>");
        int bodyEnd = html.indexOf("</body>");
        if (bodyStart >= 0 && bodyEnd > bodyStart) {
            return html.substring(bodyStart + 6, bodyEnd);
        }
        return html;
    }
}
