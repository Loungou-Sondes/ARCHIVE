package ommp.archives.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ommp.archives.dto.notification.NotificationItemDto;
import ommp.archives.dto.notification.NotificationsResponse;
import ommp.archives.entity.Bordereau;
import ommp.archives.entity.BordereauStatut;
import ommp.archives.entity.NotificationType;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.BordereauRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class NotificationService {

	private final BordereauRepository bordereauRepository;
	private final AuthorizationService authorization;

	public NotificationService(BordereauRepository bordereauRepository, AuthorizationService authorization) {
		this.bordereauRepository = bordereauRepository;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public NotificationsResponse listForCurrentUser(Authentication authentication) {
		authorization.requireAuthenticated(authentication);
		if (authorization.isAdmin(authentication)) {
			return new NotificationsResponse(0, List.of());
		}
		String userId = authorization.resolveUserAccountId(authentication.getName());
		if (userId == null || userId.isBlank()) {
			return new NotificationsResponse(0, List.of());
		}
		long count = bordereauRepository.countByAgent_IdAndStatutAndNotifAgentVue(
			userId,
			BordereauStatut.AFFECTE,
			false
		);
		List<NotificationItemDto> items = bordereauRepository
			.findTop25ByAgent_IdAndStatutAndNotifAgentVueOrderByIdDesc(userId, BordereauStatut.AFFECTE, false)
			.stream()
			.map(this::toDto)
			.toList();
		return new NotificationsResponse(count, items);
	}

	@Transactional
	public void dismissOne(Authentication authentication, Long bordereauId) {
		authorization.requireAuthenticated(authentication);
		if (authorization.isAdmin(authentication)) {
			return;
		}
		String userId = requireCurrentUserAccountId(authentication);
		Bordereau b = bordereauRepository.findById(bordereauId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Bordereau introuvable."));
		if (b.getAgent() == null || !userId.equals(b.getAgent().getId())) {
			throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Bordereau introuvable.");
		}
		if (b.getStatut() != BordereauStatut.AFFECTE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATE", "Ce bordereau n'est pas concerné.");
		}
		b.setNotifAgentVue(true);
		bordereauRepository.save(b);
	}

	@Transactional
	public void dismissAll(Authentication authentication) {
		authorization.requireAuthenticated(authentication);
		if (authorization.isAdmin(authentication)) {
			return;
		}
		String userId = requireCurrentUserAccountId(authentication);
		bordereauRepository.markAllNotifAgentVue(userId, BordereauStatut.AFFECTE);
	}

	private String requireCurrentUserAccountId(Authentication authentication) {
		String userId = authorization.resolveUserAccountId(authentication.getName());
		if (userId == null || userId.isBlank()) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Compte agent introuvable.");
		}
		return userId;
	}

	private NotificationItemDto toDto(Bordereau b) {
		String numero = b.getNumeroAffiche() != null ? b.getNumeroAffiche() : "#" + b.getId();
		return new NotificationItemDto(
			b.getId(),
			NotificationType.BORDEREAU_VALIDE,
			b.getId(),
			numero,
			"Votre bordereau " + numero + " a été validé et affecté aux emplacements.",
			null
		);
	}
}
