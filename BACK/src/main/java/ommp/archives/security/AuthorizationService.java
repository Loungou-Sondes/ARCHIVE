package ommp.archives.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import ommp.archives.entity.Boite;
import ommp.archives.entity.Bordereau;
import ommp.archives.entity.BordereauStatut;
import ommp.archives.entity.Direction;
import ommp.archives.entity.UserAccount;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.UserAccountRepository;
import ommp.archives.repository.UserDetailRepository;

/**
 * Point unique pour l'autorisation métier (admin vs agent) et la visibilité des données.
 * Les contrôles HTTP ({@link ommp.archives.config.SecurityConfiguration}) complètent ce service.
 */
@Service
public class AuthorizationService {

	private final UserAccountRepository userAccountRepository;
	private final UserDetailRepository userDetailRepository;

	public AuthorizationService(
		UserAccountRepository userAccountRepository,
		UserDetailRepository userDetailRepository
	) {
		this.userAccountRepository = userAccountRepository;
		this.userDetailRepository = userDetailRepository;
	}

	public boolean isAdmin(Authentication authentication) {
		return authentication != null
			&& authentication.isAuthenticated()
			&& !(authentication instanceof AnonymousAuthenticationToken)
			&& authentication.getAuthorities() != null
			&& authentication.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.anyMatch("ROLE_ADMIN"::equals);
	}

	public void requireAuthenticated(Authentication authentication) {
		if (authentication == null
			|| !authentication.isAuthenticated()
			|| authentication instanceof AnonymousAuthenticationToken) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentification requise.");
		}
	}

	public void requireAdmin(Authentication authentication) {
		requireAuthenticated(authentication);
		if (!isAdmin(authentication)) {
			throw new ApiException(
				HttpStatus.FORBIDDEN,
				"ADMIN_REQUIRED",
				"Action réservée à l'administrateur."
			);
		}
	}

	public String resolveUserAccountId(String username) {
		if (username == null || username.isBlank()) {
			return null;
		}
		return userAccountRepository.findByUserNameIgnoreCase(username.trim())
			.map(UserAccount::getId)
			.filter(id -> id != null && !id.isBlank())
			.orElse(null);
	}

	public String resolveUserDirectionId(String username) {
		return userAccountRepository.findByUserName(username)
			.map(UserAccount::getUserRegistrationNumber)
			.filter(r -> r != null && !r.isBlank())
			.flatMap(userDetailRepository::findByRegistrationNumber)
			.map(ud -> ud.getDirectionId())
			.filter(d -> d != null && !d.isBlank())
			.orElse(null);
	}

	public void assertCanReadBordereau(Authentication authentication, Bordereau b) {
		if (isAdmin(authentication)) {
			return;
		}
		String userAccountId = resolveUserAccountId(authentication.getName());
		String userDirectionId = resolveUserDirectionId(authentication.getName());
		if (!isBordereauVisibleToNonAdmin(b, userAccountId, userDirectionId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Accès refusé à ce bordereau.");
		}
	}

	public void assertCanReadBoite(Authentication authentication, Boite box) {
		if (isAdmin(authentication)) {
			return;
		}
		if (!isBoiteVisibleToNonAdmin(box, authentication.getName())) {
			throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Accès refusé à cette boîte.");
		}
	}

	/**
	 * Agent non admin : ses bordereaux, ceux de sa direction, et sans direction si statut AFFECTE
	 * (liste des bordereaux affectés).
	 */
	public boolean isBordereauVisibleToNonAdmin(
		Bordereau b,
		String userAccountId,
		String userDirectionId
	) {
		if (userAccountId != null
			&& !userAccountId.isBlank()
			&& b.getAgent() != null
			&& userAccountId.equals(b.getAgent().getId())) {
			return true;
		}
		Direction bordereauDir = b.getDirection();
		if (bordereauDir != null
			&& userDirectionId != null
			&& !userDirectionId.isBlank()
			&& userDirectionId.equals(bordereauDir.getId())) {
			return true;
		}
		return bordereauDir == null && b.getStatut() == BordereauStatut.AFFECTE;
	}

	/**
	 * Prédicat JPA pour filtrer les bordereaux visibles par un agent (listes paginées).
	 */
	public Predicate buildBordereauVisibilityPredicate(
		Root<Bordereau> root,
		CriteriaBuilder cb,
		String userAccountId,
		String userDirectionId,
		BordereauStatut statut
	) {
		List<Predicate> visible = new ArrayList<>();
		if (userAccountId != null && !userAccountId.isBlank()) {
			visible.add(cb.equal(root.get("agent").get("id"), userAccountId));
		}
		if (userDirectionId != null && !userDirectionId.isBlank()) {
			Join<Bordereau, Direction> dirJoin = root.join("direction", JoinType.LEFT);
			visible.add(cb.equal(dirJoin.get("id"), userDirectionId));
		}
		if (statut == BordereauStatut.AFFECTE) {
			visible.add(cb.isNull(root.get("direction")));
		}
		if (visible.isEmpty()) {
			return cb.disjunction();
		}
		return cb.or(visible.toArray(Predicate[]::new));
	}

	/** Boîtes des bordereaux créés par l'agent, même direction, ou sans direction. */
	public boolean isBoiteVisibleToNonAdmin(Boite box, String username) {
		Bordereau br = box.getBordereau();
		if (br == null) {
			return false;
		}
		String userAccountId = resolveUserAccountId(username);
		String userDirId = resolveUserDirectionId(username);

		boolean isMyBordereau = userAccountId != null
			&& br.getAgent() != null
			&& userAccountId.equals(br.getAgent().getId());

		Direction dir = br.getDirection();
		boolean sameDirection = userDirId != null
			&& dir != null
			&& userDirId.equals(dir.getId());

		boolean noDirection = dir == null;

		return isMyBordereau || sameDirection || noDirection;
	}
}
