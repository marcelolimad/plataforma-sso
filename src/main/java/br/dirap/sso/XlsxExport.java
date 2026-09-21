package br.dirap.sso;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Writes a simple XLSX file without requiring an office installation on the server. */
final class XlsxExport {
    private XlsxExport() {}

    static void write(HttpServletResponse response, String filename, String sheet,
            List<Map<String,Object>> records, List<String> columns) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
            entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "</Types>");
            entry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>");
            entry(zip, "xl/workbook.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<sheets><sheet name=\"" + escape(sheet) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(zip, "xl/_rels/workbook.xml.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                    "</Relationships>");
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            write(zip, "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            int rowNumber = 1;
            row(zip, rowNumber++, columns);
            for (Map<String,Object> record : records) {
                row(zip, rowNumber++, columns.stream().map(record::get).toList());
            }
            write(zip, "</sheetData><autoFilter ref=\"A1:" + columnName(columns.size()) + (rowNumber-1) +
                    "\"/></worksheet>");
            zip.closeEntry();
        }
    }

    private static void row(ZipOutputStream zip, int number, List<?> values) throws IOException {
        write(zip, "<row r=\"" + number + "\">");
        for (int index=0; index<values.size(); index++) {
            String ref = columnName(index+1) + number;
            Object value = values.get(index);
            if (value instanceof Number) {
                write(zip, "<c r=\"" + ref + "\"><v>" + value + "</v></c>");
            } else {
                write(zip, "<c r=\"" + ref + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" +
                        escape(value == null ? "" : value.toString()) + "</t></is></c>");
            }
        }
        write(zip, "</row>");
    }

    private static String columnName(int number) {
        StringBuilder result = new StringBuilder();
        while (number > 0) { number--; result.insert(0, (char)('A' + number % 26)); number /= 26; }
        return result.toString();
    }

    private static String escape(String text) {
        StringBuilder result = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            if (cp == '&') result.append("&amp;");
            else if (cp == '<') result.append("&lt;");
            else if (cp == '>') result.append("&gt;");
            else if (cp == '"') result.append("&quot;");
            else if (cp == '\'') result.append("&apos;");
            else if (cp == 9 || cp == 10 || cp == 13 || cp >= 32 && cp <= 0xD7FF ||
                    cp >= 0xE000 && cp <= 0xFFFD || cp >= 0x10000 && cp <= 0x10FFFF)
                result.appendCodePoint(cp);
        });
        return result.toString();
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        write(zip, content);
        zip.closeEntry();
    }

    private static void write(ZipOutputStream zip, String content) throws IOException {
        zip.write(content.getBytes(StandardCharsets.UTF_8));
    }
}
