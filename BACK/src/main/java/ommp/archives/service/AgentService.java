package ommp.archives.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import ommp.archives.dto.AgentListPageResponse;
import ommp.archives.dto.AgentResponse;
import ommp.archives.entity.UserDetail;
import ommp.archives.dto.UserManagementResponse;
import ommp.archives.entity.AgentStatusCodes;
import ommp.archives.entity.UserAccount;
import ommp.archives.entity.UserDetail;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.UserAccountRepository;
import ommp.archives.repository.UserDetailRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class AgentService {

	private final AuthService authService;
	private final UserAccountRepository userAccountRepository;
	private final UserDetailRepository userDetailRepository;
	private final AuthorizationService authorization;

	public AgentService(
		AuthService authService,
		UserAccountRepository userAccountRepository,
		UserDetailRepository userDetailRepository,
		AuthorizationService authorization
	) {
		this.authService = authService;
		this.userAccountRepository = userAccountRepository;
		this.userDetailRepository = userDetailRepository;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public AgentResponse getAgent(Authentication authentication, String userId) {
		authorization.requireAdmin(authentication);
		if (userId == null || userId.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ID", "Identifiant invalide.");
		}
		UserAccount account = userAccountRepository.findById(userId.trim())
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent introuvable."));
		UserDetail detail = resolveDetailForAccount(account).orElse(null);
		return merge(account, detail);
	}

	/**
	 * Liste paginée des comptes {@code USERS}, enrichis par {@code USER_DETAILS} quand le matricule correspond.
	 */
	@Transactional(readOnly = true)
	public AgentListPageResponse listAgents(
		Authentication authentication,
		String q,
		Boolean passwordResetOnly,
		Pageable pageable
	) {
		authorization.requireAdmin(authentication);
		String currentUserId = authorization.resolveUserAccountId(authentication.getName());
		String qTrim = trimToNull(q);
		boolean passwordResetFilter = Boolean.TRUE.equals(passwordResetOnly);
		// Demandes MDP : ne pas exclure le compte connecté (sinon compteur global ≠ liste).
		String excludeUserId = passwordResetFilter ? null : currentUserId;
		Specification<UserAccount> spec = agentListSpec(excludeUserId, qTrim, passwordResetOnly);
		Page<UserAccount> page = userAccountRepository.findAll(
			spec,
			PageRequest.of(
				pageable.getPageNumber(),
				pageable.getPageSize(),
				Sort.by(Sort.Direction.ASC, "userName")
			)
		);
		Map<String, UserDetail> detailsByReg = loadDetailsByRegistration(page.getContent());
		List<AgentResponse> content = page.getContent().stream()
			.map(account -> merge(account, resolveDetailFromMap(account, detailsByReg)))
			.toList();
		return new AgentListPageResponse(
			content,
			page.getTotalElements(),
			page.getTotalPages(),
			page.getNumber(),
			page.getSize(),
			userDetailRepository.countActiveAgents(),
			userDetailRepository.countInactiveAgents(),
			passwordResetFilter
				? page.getTotalElements()
				: userAccountRepository.countByPasswordResetRequestedTrue()
		);
	}

	private Specification<UserAccount> agentListSpec(String excludeUserId, String qTrim, Boolean passwordResetOnly) {
		return (root, query, cb) -> {
			List<Predicate> preds = new ArrayList<>();
			if (excludeUserId != null && !excludeUserId.isBlank()) {
				preds.add(cb.notEqual(root.get("id"), excludeUserId));
			}
			if (Boolean.TRUE.equals(passwordResetOnly)) {
				preds.add(cb.isTrue(root.get("passwordResetRequested")));
			}
			if (qTrim != null && !qTrim.isEmpty()) {
				String like = "%" + qTrim.toLowerCase() + "%";
				Subquery<Integer> detailSq = query.subquery(Integer.class);
				Root<UserDetail> detailRoot = detailSq.from(UserDetail.class);
				detailSq.select(cb.literal(1));
				detailSq.where(
					cb.equal(
						cb.lower(cb.function("trim", String.class, detailRoot.get("registrationNumber"))),
						cb.lower(cb.function("trim", String.class, root.get("userRegistrationNumber")))
					),
					cb.or(
						cb.like(cb.lower(detailRoot.get("firstName")), like),
						cb.like(cb.lower(detailRoot.get("lastName")), like),
						cb.like(cb.lower(detailRoot.get("directionId")), like)
					)
				);
				preds.add(cb.or(
					cb.like(cb.lower(root.get("userName")), like),
					cb.like(cb.lower(root.get("email")), like),
					cb.like(cb.lower(root.get("userRegistrationNumber")), like),
					cb.like(cb.lower(root.get("phoneNumber")), like),
					cb.like(cb.lower(root.get("role")), like),
					cb.exists(detailSq)
				));
			}
			return cb.and(preds.toArray(Predicate[]::new));
		};
	}

	@Transactional
	public AgentResponse setAgentActive(Authentication authentication, String userId, boolean active) {
		authorization.requireAdmin(authentication);
		UserAccount account = userAccountRepository.findById(userId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Utilisateur introuvable."));
		if (!active) {
			String currentUserId = authorization.resolveUserAccountId(authentication.getName());
			if (currentUserId != null && currentUserId.equals(userId)) {
				throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"CANNOT_DEACTIVATE_SELF",
					"Vous ne pouvez pas désactiver votre propre compte."
				);
			}
		}
		UserDetail detail = resolveOrCreateDetailForAccount(account);
		long targetStatus = active ? AgentStatusCodes.ACTIF : AgentStatusCodes.INACTIF;
		if (detail.getStatusId() != null && detail.getStatusId() == targetStatus) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"AGENT_ALREADY_IN_STATUS",
				active ? "Ce compte est déjà actif." : "Ce compte est déjà inactif."
			);
		}
		detail.setStatusId(targetStatus);
		userDetailRepository.save(detail);
		return merge(account, detail);
	}

	private Map<String, UserDetail> loadDetailsByRegistration(List<UserAccount> accounts) {
		List<String> regs = accounts.stream()
			.map(a -> normalizeReg(a.getUserRegistrationNumber()))
			.filter(r -> r != null)
			.map(String::toLowerCase)
			.distinct()
			.toList();
		if (regs.isEmpty()) {
			return Map.of();
		}
		return userDetailRepository.findByRegistrationNumberNormalizedIn(regs).stream()
			.collect(Collectors.toMap(
				d -> d.getRegistrationNumber().trim().toLowerCase(),
				d -> d,
				(a, b) -> a
			));
	}

	private UserDetail resolveDetailFromMap(UserAccount account, Map<String, UserDetail> detailsByReg) {
		String reg = normalizeReg(account.getUserRegistrationNumber());
		if (reg == null) {
			return null;
		}
		return detailsByReg.get(reg.toLowerCase());
	}

	private Optional<UserDetail> resolveDetailForAccount(UserAccount account) {
		String reg = normalizeReg(account.getUserRegistrationNumber());
		if (reg == null) {
			return Optional.empty();
		}
		return userDetailRepository.findByRegistrationNumberNormalized(reg);
	}

	/** Crée une fiche {@code USER_DETAILS} minimale si l’entreprise n’a provisionné que {@code USERS}. */
	private UserDetail resolveOrCreateDetailForAccount(UserAccount account) {
		String reg = normalizeReg(account.getUserRegistrationNumber());
		if (reg == null) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"REGISTRATION_REQUIRED",
				"Matricule agent manquant sur le compte USERS (USER_REGISTRATION_NUMBER)."
			);
		}
		return userDetailRepository.findByRegistrationNumberNormalized(reg)
			.orElseGet(() -> {
				UserDetail created = new UserDetail();
				created.setRegistrationNumber(reg);
				created.setStatusId(AgentStatusCodes.ACTIF);
				return userDetailRepository.save(created);
			});
	}

	private AgentResponse merge(UserManagementResponse u, UserDetail d, boolean passwordResetRequested) {
		String phone = (d != null && d.getPhoneNumber() != null && !d.getPhoneNumber().isBlank())
			? d.getPhoneNumber()
			: u.phoneNumber();
		String first = (d != null && d.getFirstName() != null) ? d.getFirstName() : u.firstName();
		return new AgentResponse(
			u.id(),
			u.userName(),
			u.email(),
			u.gender(),
			phone,
			u.role(),
			u.userRegistrationNumber(),
			d != null ? d.getLastName() : null,
			first,
			d != null ? d.getCin() : null,
			d != null ? d.getBirthDate() : null,
			d != null ? d.getRecruitmentDate() : null,
			d != null ? d.getJobId() : null,
			d != null ? d.getPositionId() : null,
			d != null ? d.getStatusId() : null,
			d != null ? d.getDirectionId() : null,
			u.harbor(),
			passwordResetRequested);
	}

	private AgentResponse merge(UserAccount account, UserDetail d) {
		String reg = account.getUserRegistrationNumber();
		UserManagementResponse synthetic = new UserManagementResponse(
			account.getId(),
			account.getUserName(),
			null,
			account.getEmail(),
			account.getGender(),
			account.getPhoneNumber(),
			account.getRole(),
			authService.readPortForUser(account.getId()),
			reg);
		return merge(synthetic, d, account.isPasswordResetRequested());
	}

	private static String normalizeReg(String r) {
		if (r == null) {
			return null;
		}
		String t = r.trim();
		return t.isEmpty() ? null : t;
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
