package devary.moyludoc.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFPicture;
import org.apache.poi.xssf.usermodel.XSSFShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@ApplicationScoped
public class SpreadsheetPreviewService {

    public SpreadsheetData parse(byte[] content) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            List<SheetData> sheets = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : workbook) {
                List<List<CellData>> rows = new ArrayList<>();
                int maxColumns = 0;
                for (Row row : sheet) {
                    int lastCell = Math.max(row.getLastCellNum(), 0);
                    maxColumns = Math.max(maxColumns, lastCell);
                    List<CellData> values = new ArrayList<>();
                    for (int i = 0; i < lastCell; i++) {
                        Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        if (cell == null) {
                            values.add(new CellData("", false, false, null, null, null));
                            continue;
                        }
                        CellStyle style = cell.getCellStyle();
                        Font font = workbook.getFontAt(style.getFontIndex());
                        String bg = null;
                        if (style.getFillPattern() == FillPatternType.SOLID_FOREGROUND
                                && style.getFillForegroundColor() != IndexedColors.AUTOMATIC.getIndex()) {
                            bg = "E5E7EB";
                        }
                        Hyperlink hyperlink = cell.getHyperlink();
                        values.add(new CellData(
                                formatter.formatCellValue(cell),
                                font.getBold(),
                                font.getItalic(),
                                font.getFontHeightInPoints() > 0 ? (double) font.getFontHeightInPoints() : null,
                                bg,
                                hyperlink != null ? hyperlink.getAddress() : null));
                    }
                    rows.add(values);
                }
                sheets.add(new SheetData(sheet.getSheetName(), rows, maxColumns, extractImages(sheet)));
            }
            return new SpreadsheetData(sheets);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse XLSX", e);
        }
    }

    private List<SheetImage> extractImages(Sheet sheet) {
        List<SheetImage> images = new ArrayList<>();
        if (sheet.getDrawingPatriarch() instanceof XSSFDrawing drawing) {
            for (XSSFShape shape : drawing.getShapes()) {
                if (shape instanceof XSSFPicture picture) {
                    byte[] data = picture.getPictureData().getData();
                    images.add(new SheetImage(
                            picture.getPictureData().suggestFileExtension(),
                            Base64.getEncoder().encodeToString(data)));
                }
            }
        }
        return images;
    }

    public String renderHtml(SpreadsheetData data, String title) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append("<title>").append(escape(title)).append("</title>")
                .append("<style>body{font-family:Inter,Arial,sans-serif;background:#f5f7fb;color:#111827;margin:0;padding:32px}.page{max-width:1100px;margin:0 auto;background:#fff;padding:32px;border-radius:16px;box-shadow:0 10px 30px rgba(0,0,0,.08)}table{border-collapse:collapse;width:100%;margin:16px 0}td,th{border:1px solid #d1d5db;padding:8px 10px;vertical-align:top}.sheet{margin-bottom:32px}.meta{color:#6b7280;font-size:14px}.sheet-img{max-width:220px;border:1px solid #cbd5e1;border-radius:10px;margin:10px 10px 0 0}</style></head><body><div class=\"page\"><h1>")
                .append(escape(title)).append("</h1><div class=\"meta\">Excel preview</div>");
        for (SheetData sheet : data.sheets()) {
            html.append("<div class=\"sheet\"><h2>").append(escape(sheet.name())).append("</h2><table>");
            for (int r = 0; r < sheet.rows().size(); r++) {
                html.append("<tr>");
                for (int c = 0; c < sheet.columnCount(); c++) {
                    CellData cell = c < sheet.rows().get(r).size() ? sheet.rows().get(r).get(c) : new CellData("", false, false, null, null, null);
                    String tag = r == 0 ? "th" : "td";
                    html.append("<").append(tag).append(" style=\"").append(css(cell)).append("\">");
                    if (cell.hyperlink() != null && !cell.hyperlink().isBlank()) {
                        html.append("<a href=\"").append(escape(cell.hyperlink())).append("\" target=\"_blank\" rel=\"noopener noreferrer\">")
                                .append(escape(cell.value())).append("</a>");
                    } else {
                        html.append(escape(cell.value()));
                    }
                    html.append("</").append(tag).append(">");
                }
                html.append("</tr>");
            }
            html.append("</table>");
            for (SheetImage image : sheet.images()) {
                html.append("<img class=\"sheet-img\" alt=\"Excel image\" src=\"data:image/")
                        .append(escape(image.format())).append(";base64,")
                        .append(image.base64Data()).append("\">");
            }
            html.append("</div>");
        }
        html.append("</div></body></html>");
        return html.toString();
    }

    private String css(CellData cell) {
        StringBuilder css = new StringBuilder();
        if (cell.bold()) css.append("font-weight:bold;");
        if (cell.italic()) css.append("font-style:italic;");
        if (cell.fontSize() != null) css.append("font-size:").append(cell.fontSize()).append("pt;");
        if (cell.background() != null && !cell.background().isBlank()) css.append("background:#").append(cell.background().replaceFirst("^FF", "")).append(";");
        return css.toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public record SpreadsheetData(List<SheetData> sheets) {}
    public record SheetData(String name, List<List<CellData>> rows, int columnCount, List<SheetImage> images) {}
    public record CellData(String value, boolean bold, boolean italic, Double fontSize, String background, String hyperlink) {}
    public record SheetImage(String format, String base64Data) {}
}
