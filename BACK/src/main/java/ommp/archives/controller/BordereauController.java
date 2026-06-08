package ommp.archives.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import ommp.archives.dto.bordereau.BlocEmplacementSuggestionDto;
import ommp.archives.dto.bordereau.BoiteBlocsSuggestionDto;
import ommp.archives.dto.bordereau.BordereauDetailResponse;
import ommp.archives.dto.bordereau.BordereauFormOptionsDto;
import ommp.archives.dto.bordereau.BordereauListItemResponse;
import ommp.archives.dto.bordereau.CreateBordereauRequest;
import ommp.archives.dto.bordereau.NextNumeroAfficheResponse;
import ommp.archives.dto.bordereau.RegleConservationValidePreviewDto;
import ommp.archives.dto.bordereau.ValiderBordereauAffectationRequest;
import ommp.archives.service.BordereauService;

@RestController
@RequestMapping("/api/bordereaux")
public class BordereauController {

	private final BordereauService bordereauService;

	public BordereauController(BordereauService bordereauService) {
		this.bordereauService = bordereauService;
	}

	@GetMapping("/form-options")
	public ResponseEntity<BordereauFormOptionsDto> formOptions(
		Authentication authentication,
		@RequestParam(value = "anneeProchainNumero", required = false) Integer anneeProchainNumero
	) {
		return ResponseEntity.ok(bordereauService.formOptions(authentication, anneeProchainNumero));
	}

	@GetMapping("/en-attente")
	public ResponseEntity<Page<BordereauListItemResponse>> listEnAttente(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) String directionId,
		@RequestParam(required = false) Integer anneeMin,
		@RequestParam(required = false) Integer anneeMax,
		@PageableDefault(size = 10, sort = "dateTransfert") Pageable pageable
	) {
		return ResponseEntity.ok(
			bordereauService.listEnAttente(authentication, q, directionId, anneeMin, anneeMax, pageable)
		);
	}

	/** File de validation : bordereaux EN_ATTENTE soumis par des agents (admin uniquement). */
	@GetMapping("/validation-agents")
	public ResponseEntity<Page<BordereauListItemResponse>> listValidationAgents(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) String directionId,
		@RequestParam(required = false) Integer anneeMin,
		@RequestParam(required = false) Integer anneeMax,
		@PageableDefault(size = 10, sort = "dateTransfert") Pageable pageable
	) {
		return ResponseEntity.ok(
			bordereauService.listValidationAgents(authentication, q, directionId, anneeMin, anneeMax, pageable)
		);
	}

	@GetMapping
	public ResponseEntity<Page<BordereauListItemResponse>> list(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) String directionId,
		@RequestParam(required = false) Integer anneeMin,
		@RequestParam(required = false) Integer anneeMax,
		@PageableDefault(size = 10, sort = "dateTransfert") Pageable pageable
	) {
		return ResponseEntity.ok(
			bordereauService.list(authentication, q, directionId, anneeMin, anneeMax, pageable)
		);
	}

	@GetMapping("/prochain-numero")
	public ResponseEntity<NextNumeroAfficheResponse> prochainNumero(
		Authentication authentication,
		@RequestParam("annee") int annee
	) {
		return ResponseEntity.ok(bordereauService.previewNextNumeroAffiche(authentication, annee));
	}

	@GetMapping("/document-types/{documentTypeId}/regle-conservation-valide")
	public ResponseEntity<RegleConservationValidePreviewDto> regleConservationValide(
		Authentication authentication,
		@PathVariable Long documentTypeId
	) {
		return ResponseEntity.ok(bordereauService.previewRegleValideForDocumentType(authentication, documentTypeId));
	}

	@GetMapping("/suggestion-emplacements-blocs")
	public ResponseEntity<List<BoiteBlocsSuggestionDto>> suggestEmplacementBlocs(
		Authentication authentication,
		@RequestParam("metrageCm") List<Integer> metragesCm
	) {
		return ResponseEntity.ok(bordereauService.suggestEmplacementBlocs(authentication, metragesCm));
	}

	@GetMapping("/blocs-libres")
	public ResponseEntity<Page<BlocEmplacementSuggestionDto>> listBlocsLibres(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@PageableDefault(size = 50) Pageable pageable
	) {
		return ResponseEntity.ok(bordereauService.listBlocsLibres(authentication, q, pageable));
	}

	@GetMapping("/{id}")
	public ResponseEntity<BordereauDetailResponse> getById(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(bordereauService.getById(authentication, id));
	}

	@PostMapping
	public ResponseEntity<BordereauDetailResponse> create(
		Authentication authentication,
		@Valid @RequestBody CreateBordereauRequest request
	) {
		return ResponseEntity.ok(bordereauService.create(authentication, request));
	}

	@PostMapping("/{id}/valider-affectation")
	public ResponseEntity<BordereauDetailResponse> validerAffectation(
		Authentication authentication,
		@PathVariable Long id,
		@RequestBody(required = false) @Valid ValiderBordereauAffectationRequest request
	) {
		return ResponseEntity.ok(bordereauService.validerAffectation(authentication, id, request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<BordereauDetailResponse> updateEnAttente(
		Authentication authentication,
		@PathVariable Long id,
		@Valid @RequestBody CreateBordereauRequest request
	) {
		return ResponseEntity.ok(bordereauService.updateEnAttente(authentication, id, request));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> deleteEnAttente(Authentication authentication, @PathVariable Long id) {
		bordereauService.deleteEnAttente(authentication, id);
		return ResponseEntity.noContent().build();
	}
}
