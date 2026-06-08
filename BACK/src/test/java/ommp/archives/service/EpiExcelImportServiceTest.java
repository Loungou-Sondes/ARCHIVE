package ommp.archives.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EpiExcelImportServiceTest {

	@Test
	void buildTemplateWorkbook_producesNonEmptyXlsx() {
		byte[] bytes = new EpiExcelImportService().buildTemplateWorkbook();
		assertTrue(bytes.length > 500, "template should be a valid xlsx payload");
	}
}
