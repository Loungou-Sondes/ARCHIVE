package ommp.archives.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import ommp.archives.dto.documenttype.CreateDocumentTypeRequest;
import ommp.archives.dto.documenttype.DirectionOptionDto;
import ommp.archives.dto.documenttype.DocumentTypeResponse;
import ommp.archives.dto.documenttype.UpdateDocumentTypeRequest;
import ommp.archives.service.DocumentTypeService;

@RestController
@RequestMapping("/api/document-types")
public class DocumentTypeController {

	private final DocumentTypeService documentTypeService;

	public DocumentTypeController(DocumentTypeService documentTypeService) {
		this.documentTypeService = documentTypeService;
	}

	@GetMapping("/direction-options")
	public ResponseEntity<List<DirectionOptionDto>> directionOptions(Authentication authentication) {
		return ResponseEntity.ok(documentTypeService.listDirectionOptions(authentication));
	}

	@GetMapping
	public ResponseEntity<Page<DocumentTypeResponse>> list(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) String directionId,
		@PageableDefault(size = 10, sort = "id") Pageable pageable
	) {
		return ResponseEntity.ok(documentTypeService.list(authentication, q, directionId, pageable));
	}

	@GetMapping("/{id}")
	public ResponseEntity<DocumentTypeResponse> getById(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(documentTypeService.getById(authentication, id));
	}

	@PostMapping
	public ResponseEntity<DocumentTypeResponse> create(
		Authentication authentication,
		@Valid @RequestBody CreateDocumentTypeRequest request
	) {
		return ResponseEntity.ok(documentTypeService.create(authentication, request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<DocumentTypeResponse> update(
		Authentication authentication,
		@PathVariable Long id,
		@Valid @RequestBody UpdateDocumentTypeRequest request
	) {
		return ResponseEntity.ok(documentTypeService.update(authentication, id, request));
	}
}
