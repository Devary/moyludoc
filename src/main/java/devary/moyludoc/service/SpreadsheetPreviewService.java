package devary.moyludoc.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@ApplicationScoped
public class SpreadsheetPreviewService {

    public SpreadsheetData parse(byte[] content) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            List<SheetData> sheets = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();
            for (Sheet sheet : workbook) {
                List<List<String>> rows = new ArrayList<>();
                int maxColumns = 0;
                for (Row row : sheet) {
                    int lastCell = Math.max(row.getLastCellNum(), 0);
                    maxColumns = Math.max(maxColumns, lastCell);
                    List<String> values = new ArrayList<>();
                    for (int i = 0; i < lastCell; i++) {
                        Cell cell = row.getCell(i, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        values.add(cell == null ? "" : formatter.formatCellValue(cell));
                    }
                    rows.add(values);
                }
                sheets.add(new SheetData(sheet.getSheetName(), rows, maxColumns));
            }
            return new SpreadsheetData(sheets);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse XLSX", e);
        }
    }

    public String renderHtml(SpreadsheetData data, String title) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append("<title>").append(escape(title)).append("</title>")
                .append("<style>body{font-family:Inter,Arial,sans-serif;background:#f5f7fb;color:#111827;margin:0;padding:32px}.page{max-width:1100px;margin:0 auto;background:#fff;padding:32px;border-radius:16px;box-shadow:0 10px 30px rgba(0,0,0,.08)}table{border-collapse:collapse;width:100%;margin:16px 0}td,th{border:1px solid #d1d5db;padding:8px 10px;vertical-align:top}.sheet{margin-bottom:32px}.meta{color:#6b7280;font-size:14px}</style></head><body><div class=\"page\"><h1>")
                .append(escape(title)).append("</h1><div class=\"meta\">Excel preview</div>");
        for (SheetData sheet : data.sheets()) {
            html.append("<div class=\"sheet\"><h2>").append(escape(sheet.name())).append("</h2><table>");
            for (int r = 0; r < sheet.rows().size(); r++) {
                html.append("<tr>");
                for (int c = 0; c < sheet.columnCount(); c++) {
                    String value = c < sheet.rows().get(r).size() ? sheet.rows().get(r).get(c) : "";
                    html.append(r == 0 ? "<th>" : "<td>").append(escape(value)).append(r == 0 ? "</th>" : "</td>");
                }
                html.append("</tr>");
            }
            html.append("</table></div>");
        }
        html.append("</div></body></html>");
        return html.toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public record SpreadsheetData(List<SheetData> sheets) {}
    public record SheetData(String name, List<List<String>> rows, int columnCount) {}
}
