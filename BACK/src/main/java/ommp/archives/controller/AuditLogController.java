package ommp.archives.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ommp.archives.dto.audit.AuditLogItemDto;
import ommp.archives.service.AuditService;

@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

	private final AuditService auditService;

	public AuditLogController(AuditService auditService) {
		this.auditService = auditService;
	}

	@GetMapping("/logs")
	public ResponseEntity<Page<AuditLogItemDto>> list(
		Authentication authentication,
		@PageableDefault(size = 50, sort = "createdAt") Pageable pageable
	) {
		return ResponseEntity.ok(auditService.listRecent(authentication, pageable));
	}
}
