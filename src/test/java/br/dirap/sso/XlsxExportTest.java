package br.dirap.sso;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class XlsxExportTest {
    @Test void exportsValidWorkbookAndEscapesPatientText() throws Exception {
        var response = new MockHttpServletResponse();
        Map<String,Object> record = new HashMap<>();
        record.put("ID", 42);
        record.put("Paciente", "João & Maria <teste>");
        XlsxExport.write(response, "atendimentos.xlsx", "Atendimentos", List.of(record), List.of("ID", "Paciente"));

        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", response.getContentType());
        Map<String,byte[]> parts = new HashMap<>();
        try (var zip = new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) parts.put(entry.getName(), zip.readAllBytes());
        }
        assertTrue(parts.containsKey("[Content_Types].xml"));
        assertTrue(parts.containsKey("xl/workbook.xml"));
        var xml = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(parts.get("xl/worksheets/sheet1.xml")));
        assertEquals(2, xml.getElementsByTagName("row").getLength());
        assertEquals("João & Maria <teste>", xml.getElementsByTagName("t").item(2).getTextContent());
    }
}
