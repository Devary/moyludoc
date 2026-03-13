package devary.moyludoc.resource;

import devary.moyludoc.service.DocxHtmlRendererService;
import devary.moyludoc.service.DocxParsingService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import org.jboss.resteasy.reactive.MultipartForm;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Path("/api/docx")
@Produces(MediaType.APPLICATION_JSON)
public class DocxParsingResource {

    private static final List<String> SAMPLE_FILES = List.of(
            "sample-basic.docx",
            "sample-list-and-table.docx",
            "sample-styles-showcase.docx");

    @Inject
    DocxParsingService docxParsingService;

    @Inject
    DocxHtmlRendererService docxHtmlRendererService;

    @GET
    @Path("/samples")
    public Uni<List<String>> samples() {
        return Uni.createFrom().item(SAMPLE_FILES);
    }

    @GET
    @Path("/samples/{fileName}/extract")
    public Uni<DocxParsingService.ParsedDocx> extractSample(@PathParam("fileName") String fileName) {
        return Uni.createFrom().item(() -> docxParsingService.parse(readSample(fileName)));
    }

    @GET
    @Path("/samples/{fileName}/preview")
    @Produces(MediaType.TEXT_HTML)
    public Uni<String> previewSample(@PathParam("fileName") String fileName) {
        return Uni.createFrom().item(() -> {
            DocxParsingService.ParsedDocx parsedDocx = docxParsingService.parse(readSample(fileName));
            return docxHtmlRendererService.renderDocument(parsedDocx, fileName);
        });
    }

    @POST
    @Path("/extract")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Uni<DocxParsingService.ParsedDocx> extract(@MultipartForm UploadForm form) {
        return Uni.createFrom().item(() -> docxParsingService.parse(readUpload(form)));
    }

    @POST
    @Path("/preview")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.TEXT_HTML)
    public Uni<String> preview(@MultipartForm UploadForm form) {
        return Uni.createFrom().item(() -> {
            byte[] content = readUpload(form);
            DocxParsingService.ParsedDocx parsedDocx = docxParsingService.parse(content);
            String title = form.file != null && form.file.fileName() != null ? form.file.fileName() : "Uploaded DOCX";
            return docxHtmlRendererService.renderDocument(parsedDocx, title);
        });
    }

    private byte[] readSample(String fileName) {
        if (!SAMPLE_FILES.contains(fileName)) {
            throw new WebApplicationException("Unknown sample file: " + fileName, Response.Status.NOT_FOUND);
        }

        try (InputStream inputStream = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream("samples/" + fileName)) {
            if (inputStream == null) {
                throw new WebApplicationException(
                        "Sample file not found in resources: " + fileName,
                        Response.Status.NOT_FOUND);
            }
            return inputStream.readAllBytes();
        } catch (IOException e) {
            throw new WebApplicationException("Failed to read sample DOCX: " + fileName, e);
        }
    }

    private byte[] readUpload(UploadForm form) {
        if (form == null || form.file == null) {
            throw new WebApplicationException("A DOCX file is required in form field 'file'", Response.Status.BAD_REQUEST);
        }

        try {
            return Files.readAllBytes(form.file.uploadedFile());
        } catch (IOException e) {
            throw new WebApplicationException("Failed to read uploaded DOCX file", e);
        }
    }

    public static class UploadForm {
        @RestForm("file")
        public FileUpload file;
    }
}
