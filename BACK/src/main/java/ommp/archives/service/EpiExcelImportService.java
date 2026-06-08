package ommp.archives.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import ommp.archives.dto.emplacement.CreateEpiRequest;
import ommp.archives.exception.ApiException;

@Service
public class EpiExcelImportService {

	private static final int MAX_IMPORT_ROWS = 100;
	/** Lignes vides du modèle (blocs = 8, métrage = 10) ; seules les lignes avec travées + tablettes sont importées. */
	private static final int TEMPLATE_DATA_ROWS = 20;
	private static final int DEFAULT_BLOCS_PER_TABLETTE = 8;
	private static final int DEFAULT_BLOC_LINEAR_CM = 10;
	private static final DataFormatter CELL_FORMAT = new DataFormatter(Locale.FRENCH);

	private static final String[] TEMPLATE_HEADERS = {
		"Nombre de travées",
		"Nombre de tablettes",
		"Nombre de blocs",
		"Métrage bloc (cm)"
	};

	public byte[] buildTemplateWorkbook() {
		try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Import épis");
			Row header = sheet.createRow(0);
			for (int i = 0; i < TEMPLATE_HEADERS.length; i++) {
				header.createCell(i).setCellValue(TEMPLATE_HEADERS[i]);
			}
			for (int r = 1; r <= TEMPLATE_DATA_ROWS; r++) {
				Row dataRow = sheet.createRow(r);
				dataRow.createCell(2).setCellValue(DEFAULT_BLOCS_PER_TABLETTE);
				dataRow.createCell(3).setCellValue(DEFAULT_BLOC_LINEAR_CM);
			}
			// Largeurs fixes : autoSizeColumn échoue souvent sans polices AWT (erreur 500 au téléchargement).
			int[] colChars = { 22, 24, 18, 22 };
			for (int i = 0; i < colChars.length; i++) {
				sheet.setColumnWidth(i, colChars[i] * 256);
			}
			workbook.write(out);
			return out.toByteArray();
		} catch (IOException ex) {
			throw new ApiException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"TEMPLATE_BUILD_ERROR",
				"Impossible de générer le modèle Excel."
			);
		}
	}

	public List<CreateEpiRequest> parseAndValidate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_EMPTY", "Aucun fichier sélectionné.");
		}
		String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
		if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"INVALID_FILE_TYPE",
				"Format attendu : fichier Excel (.xlsx ou .xls)."
			);
		}

		List<CreateEpiRequest> rows = new ArrayList<>();
		List<String> errors = new ArrayList<>();

		try (InputStream in = file.getInputStream(); Workbook workbook = WorkbookFactory.create(in)) {
			Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
			if (sheet == null) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "SHEET_EMPTY", "Le classeur ne contient aucune feuille.");
			}

			int firstRow = sheet.getFirstRowNum();
			int lastRow = sheet.getLastRowNum();
			boolean headerSkipped = false;

			for (int r = firstRow; r <= lastRow; r++) {
				Row row = sheet.getRow(r);
				if (row == null) {
					continue;
				}
				int excelLine = r + 1;
				if (!headerSkipped && looksLikeHeaderRow(row)) {
					headerSkipped = true;
					continue;
				}
				headerSkipped = true;

				String traversRaw = cellText(row, 0);
				String tablettesRaw = cellText(row, 1);
				if (traversRaw.isBlank() && tablettesRaw.isBlank()) {
					continue;
				}
				if (traversRaw.isBlank() || tablettesRaw.isBlank()) {
					if (traversRaw.isBlank()) {
						errors.add("Ligne " + excelLine + " : « Nombre de travées » est obligatoire pour créer un épi.");
					}
					if (tablettesRaw.isBlank()) {
						errors.add("Ligne " + excelLine + " : « Nombre de tablettes » est obligatoire pour créer un épi.");
					}
					continue;
				}

				Integer travers = readPositiveInt(row, 0, excelLine, "Nombre de travées", 1, 9, errors);
				Integer tablettes = readPositiveInt(row, 1, excelLine, "Nombre de tablettes", 1, 9, errors);
				Integer blocs = readPositiveIntOrDefault(
					row, 2, excelLine, "Nombre de blocs", 1, 8, DEFAULT_BLOCS_PER_TABLETTE, errors
				);
				Integer metrage = readPositiveIntOrDefault(
					row, 3, excelLine, "Métrage bloc (cm)", 1, 500, DEFAULT_BLOC_LINEAR_CM, errors
				);

				if (travers == null || tablettes == null || blocs == null || metrage == null) {
					continue;
				}
				rows.add(new CreateEpiRequest(null, travers, tablettes, blocs, metrage));
				if (rows.size() > MAX_IMPORT_ROWS) {
					throw new ApiException(
						HttpStatus.BAD_REQUEST,
						"TOO_MANY_ROWS",
						"Maximum " + MAX_IMPORT_ROWS + " épis par import."
					);
				}
			}
		} catch (ApiException ex) {
			throw ex;
		} catch (IOException ex) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"FILE_READ_ERROR",
				"Impossible de lire le fichier Excel : " + ex.getMessage()
			);
		} catch (Exception ex) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"FILE_PARSE_ERROR",
				"Fichier Excel invalide ou illisible."
			);
		}

		if (!errors.isEmpty()) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"IMPORT_VALIDATION_FAILED",
				String.join(" ", errors)
			);
		}
		if (rows.isEmpty()) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"NO_DATA_ROWS",
				"Aucun épi à créer : renseignez au moins les colonnes « Nombre de travées » et « Nombre de tablettes » sur une ou plusieurs lignes."
			);
		}
		return rows;
	}

	private static boolean looksLikeHeaderRow(Row row) {
		String first = cellText(row, 0).toLowerCase(Locale.ROOT);
		return first.contains("trav") || first.contains("tablette") || first.contains("bloc") || first.contains("métrage")
			|| first.contains("metrage");
	}

	private static String cellText(Row row, int col) {
		Cell cell = row.getCell(col);
		if (cell == null) {
			return "";
		}
		return CELL_FORMAT.formatCellValue(cell).trim();
	}

	private static Integer readPositiveInt(
		Row row,
		int col,
		int excelLine,
		String label,
		int min,
		int max,
		List<String> errors
	) {
		String raw = cellText(row, col);
		if (raw.isEmpty()) {
			errors.add("Ligne " + excelLine + " : « " + label + " » est obligatoire.");
			return null;
		}
		int value;
		try {
			value = (int) Math.round(Double.parseDouble(raw.replace(',', '.')));
		} catch (NumberFormatException ex) {
			errors.add("Ligne " + excelLine + " : « " + label + " » doit être un nombre entier.");
			return null;
		}
		if (value < min || value > max) {
			errors.add("Ligne " + excelLine + " : « " + label + " » doit être entre " + min + " et " + max + ".");
			return null;
		}
		return value;
	}

	private static Integer readPositiveIntOrDefault(
		Row row,
		int col,
		int excelLine,
		String label,
		int min,
		int max,
		int defaultValue,
		List<String> errors
	) {
		String raw = cellText(row, col);
		if (raw.isEmpty()) {
			return defaultValue;
		}
		return readPositiveInt(row, col, excelLine, label, min, max, errors);
	}
}
