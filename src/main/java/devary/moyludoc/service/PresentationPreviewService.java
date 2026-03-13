package devary.moyludoc.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

@ApplicationScoped
public class PresentationPreviewService {

    public PresentationData parse(byte[] content) {
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(content))) {
            List<SlideData> slides = new ArrayList<>();
            int index = 1;
            for (XSLFSlide slide : slideShow.getSlides()) {
                List<String> texts = new ArrayList<>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank()) {
                            texts.add(text);
                        }
                    }
                }
                slides.add(new SlideData(index++, slide.getTitle(), texts, renderSlideThumbnail(slideShow, slide)));
            }
            return new PresentationData(slides);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse PPTX", e);
        }
    }

    private String renderSlideThumbnail(XMLSlideShow slideShow, XSLFSlide slide) {
        try {
            BufferedImage img = new BufferedImage(640, 360, BufferedImage.TYPE_INT_RGB);
            var g = img.createGraphics();
            g.setPaint(java.awt.Color.WHITE);
            g.fillRect(0, 0, img.getWidth(), img.getHeight());
            slide.draw(g);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return Base64.getEncoder().encodeToString(out.toByteArray());
        } catch (IOException e) {
            return "";
        }
    }

    public String renderHtml(PresentationData data, String title) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append("<title>").append(escape(title)).append("</title>")
                .append("<style>body{font-family:Inter,Arial,sans-serif;background:#eef2ff;color:#111827;margin:0;padding:32px}.page{max-width:1100px;margin:0 auto}.slide{background:#fff;padding:28px;border-radius:18px;box-shadow:0 10px 30px rgba(0,0,0,.08);margin-bottom:24px}.meta{color:#6b7280;font-size:14px}.thumb{max-width:100%;border-radius:12px;border:1px solid #dbeafe;margin-bottom:12px}</style></head><body><div class=\"page\"><h1>")
                .append(escape(title)).append("</h1><div class=\"meta\">PowerPoint preview</div>");
        for (SlideData slide : data.slides()) {
            html.append("<section class=\"slide\"><div class=\"meta\">Slide ").append(slide.index()).append("</div><h2>")
                    .append(escape(slide.title() == null || slide.title().isBlank() ? "Untitled slide" : slide.title()))
                    .append("</h2>");
            if (slide.thumbnailBase64() != null && !slide.thumbnailBase64().isBlank()) {
                html.append("<img class=\"thumb\" alt=\"slide thumbnail\" src=\"data:image/png;base64,")
                        .append(slide.thumbnailBase64()).append("\">");
            }
            for (String text : slide.texts()) {
                html.append("<p>").append(escape(text)).append("</p>");
            }
            html.append("</section>");
        }
        html.append("</div></body></html>");
        return html.toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public record PresentationData(List<SlideData> slides) {}
    public record SlideData(int index, String title, List<String> texts, String thumbnailBase64) {}
}
