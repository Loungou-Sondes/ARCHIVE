package ommp.archives.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import ommp.archives.dto.emplacement.CreateEpiRequest;
import ommp.archives.dto.emplacement.EpiBulkImportResult;
import ommp.archives.dto.emplacement.ResizeEpiRequest;
import ommp.archives.dto.emplacement.EpiListPageResponse;
import ommp.archives.dto.emplacement.EpiMatrixDto;
import ommp.archives.dto.emplacement.EpiSummaryDto;
import ommp.archives.dto.emplacement.NextEpiNumeroDto;
import ommp.archives.dto.emplacement.TabletteFillAlertPageResponse;
import ommp.archives.service.EmplacementService;

@RestController
@RequestMapping("/api/emplacements")
public class EmplacementController {

	private final EmplacementService emplacementService;

	public EmplacementController(EmplacementService emplacementService) {
		this.emplacementService = emplacementService;
	}

	@GetMapping("/epis")
	public ResponseEntity<EpiListPageResponse> list(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@PageableDefault(size = 12) Pageable pageable
	) {
		return ResponseEntity.ok(emplacementService.listEpis(authentication, q, pageable));
	}

	@GetMapping("/alertes-lignes-pleines")
	public ResponseEntity<TabletteFillAlertPageResponse> listLignesPresquePleines(
		Authentication authentication,
		@RequestParam(defaultValue = "75") int seuil,
		@PageableDefault(size = 10) Pageable pageable
	) {
		return ResponseEntity.ok(emplacementService.listLignesPresquePleines(authentication, seuil, pageable));
	}

	@GetMapping("/epis/next-numero")
	public ResponseEntity<NextEpiNumeroDto> nextNumero(Authentication authentication) {
		return ResponseEntity.ok(emplacementService.previewNextNumero(authentication));
	}

	@PostMapping("/epis")
	public ResponseEntity<EpiSummaryDto> create(
		Authentication authentication,
		@Valid @RequestBody CreateEpiRequest request
	) {
		return ResponseEntity.ok(emplacementService.createEpi(authentication, request));
	}

	@GetMapping("/epis/import-template")
	public ResponseEntity<byte[]> downloadEpiImportTemplate(Authentication authentication) {
		byte[] body = emplacementService.buildEpiImportTemplate(authentication);
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"modele-import-epis.xlsx\"")
			.contentType(MediaType.parseMediaType(
				"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
			))
			.body(body);
	}

	@PostMapping(value = "/epis/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<EpiBulkImportResult> importEpisFromExcel(
		Authentication authentication,
		@RequestParam("file") MultipartFile file
	) {
		return ResponseEntity.ok(emplacementService.importEpisFromExcel(authentication, file));
	}

	@DeleteMapping("/epis/last")
	public ResponseEntity<EpiSummaryDto> deleteLast(Authentication authentication) {
		return ResponseEntity.ok(emplacementService.deleteLastEpi(authentication));
	}

	@PutMapping("/epis/{id}/resize")
	public ResponseEntity<EpiSummaryDto> resize(
		Authentication authentication,
		@PathVariable String id,
		@Valid @RequestBody ResizeEpiRequest request
	) {
		return ResponseEntity.ok(emplacementService.resizeEpi(authentication, id, request));
	}

	@GetMapping("/epis/{id}/matrix")
	public ResponseEntity<EpiMatrixDto> matrix(Authentication authentication, @PathVariable String id) {
		return ResponseEntity.ok(emplacementService.getEpiMatrix(authentication, id));
	}

	@PostMapping("/epis/{id}/rows")
	public ResponseEntity<EpiMatrixDto> addRow(Authentication authentication, @PathVariable String id) {
		return ResponseEntity.ok(emplacementService.addTabletteRow(authentication, id));
	}

	@DeleteMapping("/epis/{id}/rows/last")
	public ResponseEntity<EpiMatrixDto> removeLastRow(Authentication authentication, @PathVariable String id) {
		return ResponseEntity.ok(emplacementService.removeLastTabletteRow(authentication, id));
	}

	@PostMapping("/epis/{id}/columns")
	public ResponseEntity<EpiMatrixDto> addColumn(Authentication authentication, @PathVariable String id) {
		return ResponseEntity.ok(emplacementService.addTraversColumn(authentication, id));
	}

	@DeleteMapping("/epis/{id}/columns/last")
	public ResponseEntity<EpiMatrixDto> removeLastColumn(Authentication authentication, @PathVariable String id) {
		return ResponseEntity.ok(emplacementService.removeLastTraversColumn(authentication, id));
	}
}
