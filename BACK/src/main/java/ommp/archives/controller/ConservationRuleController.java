package ommp.archives.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import ommp.archives.dto.conservationrule.ConservationRuleInvalidationPreviewDto;
import ommp.archives.dto.conservationrule.ConservationRuleResponse;
import ommp.archives.dto.conservationrule.DurationAlertFilter;
import ommp.archives.dto.conservationrule.CreateConservationRuleRequest;
import ommp.archives.dto.conservationrule.InvalidateConservationRuleRequest;
import ommp.archives.dto.conservationrule.UpdateConservationRuleRequest;
import ommp.archives.dto.conservationrule.UpdateConservationRuleStatusRequest;
import ommp.archives.entity.ConservationRuleStatus;
import ommp.archives.entity.FinalDecision;
import ommp.archives.service.ConservationRuleService;

@RestController
@RequestMapping("/api/conservation-rules")
public class ConservationRuleController {

	private final ConservationRuleService conservationRuleService;

	public ConservationRuleController(ConservationRuleService conservationRuleService) {
		this.conservationRuleService = conservationRuleService;
	}

	@GetMapping
	public ResponseEntity<Page<ConservationRuleResponse>> list(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) ConservationRuleStatus status,
		@RequestParam(required = false) FinalDecision finalDecision,
		@RequestParam(required = false) DurationAlertFilter durationAlert,
		@PageableDefault(size = 10, sort = "id") Pageable pageable
	) {
		return ResponseEntity.ok(conservationRuleService.list(authentication, q, status, finalDecision, durationAlert, pageable));
	}

	@GetMapping("/{id}")
	public ResponseEntity<ConservationRuleResponse> getById(Authentication authentication, @PathVariable Long id) {
		return ResponseEntity.ok(conservationRuleService.getById(authentication, id));
	}

	@PostMapping
	public ResponseEntity<ConservationRuleResponse> create(
		Authentication authentication,
		@Valid @RequestBody CreateConservationRuleRequest request
	) {
		return ResponseEntity.ok(conservationRuleService.create(authentication, request));
	}

	@PutMapping("/{id}")
	public ResponseEntity<ConservationRuleResponse> update(
		Authentication authentication,
		@PathVariable Long id,
		@Valid @RequestBody UpdateConservationRuleRequest request
	) {
		return ResponseEntity.ok(conservationRuleService.update(authentication, id, request));
	}

	@GetMapping("/{id}/invalidation-preview")
	public ResponseEntity<ConservationRuleInvalidationPreviewDto> invalidationPreview(
		Authentication authentication,
		@PathVariable Long id
	) {
		return ResponseEntity.ok(conservationRuleService.invalidationPreview(authentication, id));
	}

	@PostMapping("/{id}/invalidate")
	public ResponseEntity<ConservationRuleResponse> invalidate(
		Authentication authentication,
		@PathVariable Long id,
		@Valid @RequestBody InvalidateConservationRuleRequest request
	) {
		return ResponseEntity.ok(conservationRuleService.invalidateWithStrategy(authentication, id, request));
	}

	@PostMapping("/{oldRuleId}/replace")
	public ResponseEntity<ConservationRuleResponse> replace(
		Authentication authentication,
		@PathVariable Long oldRuleId,
		@Valid @RequestBody CreateConservationRuleRequest request
	) {
		return ResponseEntity.ok(conservationRuleService.replaceValideRule(authentication, oldRuleId, request));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<ConservationRuleResponse> updateStatus(
		Authentication authentication,
		@PathVariable Long id,
		@Valid @RequestBody UpdateConservationRuleStatusRequest request
	) {
		return ResponseEntity.ok(conservationRuleService.updateStatus(authentication, id, request));
	}
}
