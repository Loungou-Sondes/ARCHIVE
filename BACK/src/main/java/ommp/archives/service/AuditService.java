package ommp.archives.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ommp.archives.audit.AuditEntry;
import ommp.archives.dto.audit.AuditLogItemDto;
import ommp.archives.entity.AuditLog;
import ommp.archives.repository.AuditLogRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class AuditService {

	private static final Logger log = LoggerFactory.getLogger(AuditService.class);
	private static final int DETAIL_MAX = 500;

	private final AuditLogRepository auditLogRepository;
	private final AuthorizationService authorization;

	public AuditService(AuditLogRepository auditLogRepository, AuthorizationService authorization) {
		this.auditLogRepository = auditLogRepository;
		this.authorization = authorization;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(String userName, AuditEntry entry) {
		if (entry == null) {
			return;
		}
		try {
			AuditLog row = new AuditLog();
			row.setCreatedAt(Instant.now());
			row.setUserName(trimTo(userName, 255));
			row.setActionCode(entry.actionCode());
			row.setResourceType(trimTo(entry.resourceType(), 32));
			row.setDetail(trimTo(entry.detail(), DETAIL_MAX));
			auditLogRepository.save(row);
		} catch (RuntimeException ex) {
			log.warn("Échec enregistrement audit [{}] : {}", entry.actionCode(), ex.getMessage());
		}
	}

	public void recordFromSecurityContext(AuditEntry entry) {
		record(resolveCurrentUserName(), entry);
	}

	@Transactional(readOnly = true)
	public Page<AuditLogItemDto> listRecent(Authentication authentication, Pageable pageable) {
		authorization.requireAdmin(authentication);
		return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toDto);
	}

	private AuditLogItemDto toDto(AuditLog row) {
		return new AuditLogItemDto(
			row.getId(),
			row.getCreatedAt(),
			row.getUserName(),
			row.getActionCode(),
			row.getResourceType(),
			row.getDetail()
		);
	}

	private String resolveCurrentUserName() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) {
			return null;
		}
		String name = auth.getName();
		return name == null || name.isBlank() ? null : name;
	}

	private static String trimTo(String value, int maxLen) {
		if (value == null) {
			return null;
		}
		String t = value.trim();
		if (t.isEmpty()) {
			return null;
		}
		return t.length() <= maxLen ? t : t.substring(0, maxLen);
	}
}
