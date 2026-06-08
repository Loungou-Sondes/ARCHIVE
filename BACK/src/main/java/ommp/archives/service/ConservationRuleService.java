package ommp.archives.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import ommp.archives.dto.conservationrule.ConservationRuleInvalidationPreviewDto;
import ommp.archives.dto.conservationrule.ConservationRuleLinkedBoiteDto;
import ommp.archives.dto.conservationrule.ConservationRuleLinkedBordereauDto;
import ommp.archives.dto.conservationrule.ConservationRuleResponse;
import ommp.archives.dto.conservationrule.DurationAlertFilter;
import ommp.archives.dto.conservationrule.CreateConservationRuleRequest;
import ommp.archives.dto.conservationrule.DocumentTypeMiniDto;
import ommp.archives.dto.conservationrule.InvalidateConservationRuleRequest;
import ommp.archives.dto.conservationrule.InvalidationBoiteStrategy;
import ommp.archives.dto.conservationrule.UpdateConservationRuleRequest;
import ommp.archives.dto.conservationrule.UpdateConservationRuleStatusRequest;
import ommp.archives.entity.Boite;
import ommp.archives.entity.ConservationRule;
import ommp.archives.entity.ConservationRuleStatus;
import ommp.archives.entity.DocumentType;
import ommp.archives.entity.FinalDecision;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.BoiteRepository;
import ommp.archives.repository.ConservationRuleRepository;
import ommp.archives.repository.DocumentTypeRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class ConservationRuleService {

	private final ConservationRuleRepository conservationRuleRepository;
	private final DocumentTypeRepository documentTypeRepository;
	private final BoiteRepository boiteRepository;
	private final AuthorizationService authorization;

	public ConservationRuleService(
		ConservationRuleRepository conservationRuleRepository,
		DocumentTypeRepository documentTypeRepository,
		BoiteRepository boiteRepository,
		AuthorizationService authorization
	) {
		this.conservationRuleRepository = conservationRuleRepository;
		this.documentTypeRepository = documentTypeRepository;
		this.boiteRepository = boiteRepository;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public Page<ConservationRuleResponse> list(
		Authentication authentication,
		String q,
		ConservationRuleStatus status,
		FinalDecision finalDecision,
		DurationAlertFilter durationAlert,
		Pageable pageable
	) {
		requireAdmin(authentication);
		String qTrim = q == null ? null : q.trim();
		Specification<ConservationRule> spec = filterSpec(
			qTrim == null || qTrim.isEmpty() ? null : qTrim,
			status,
			finalDecision,
			durationAlert
		);
		return conservationRuleRepository.findAll(spec, pageable).map(this::toResponse);
	}

	@Transactional(readOnly = true)
	public ConservationRuleResponse getById(Authentication authentication, Long id) {
		requireAdmin(authentication);
		ConservationRule rule = conservationRuleRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Règle de conservation introuvable."));
		return toResponse(rule);
	}

	@Transactional
	public ConservationRuleResponse create(Authentication authentication, CreateConservationRuleRequest request) {
		requireAdmin(authentication);
		String ref = request.reference().trim();
		FinalDecision fd = request.finalDecision();
		validateDurations(fd, request.activeUnknown(), request.activeYears(), request.semiActiveUnknown(), request.semiActiveYears());
		if (ref.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REFERENCE", "Référence obligatoire.");
		}
		if (conservationRuleRepository.existsByReferenceIgnoreCaseAndDocumentType_IdAndStatus(
			ref,
			request.documentTypeId(),
			ConservationRuleStatus.VALIDE
		)) {
			throw duplicateReferenceException();
		}
		ensureAtMostOneValideRulePerDocumentType(request.documentTypeId(), null);
		return toResponse(conservationRuleRepository.save(buildValideRuleFromCreateRequest(request, ref, fd)));
	}

	/**
	 * Remplacement atomique : enregistre la nouvelle règle, rattache les boîtes, puis invalide l’ancienne
	 * (même référence autorisée le temps de la transaction — pas d’unicité globale sur {@code REFERENCE_REGLE}).
	 */
	@Transactional
	public ConservationRuleResponse replaceValideRule(
		Authentication authentication,
		Long oldRuleId,
		CreateConservationRuleRequest request
	) {
		requireAdmin(authentication);
		String ref = request.reference().trim();
		FinalDecision fd = request.finalDecision();
		validateDurations(fd, request.activeUnknown(), request.activeYears(), request.semiActiveUnknown(), request.semiActiveYears());
		if (ref.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REFERENCE", "Référence obligatoire.");
		}
		ConservationRule oldRule = requireReplacingRuleForCreate(oldRuleId, ref, request.documentTypeId());
		if (conservationRuleRepository.existsByReferenceIgnoreCaseAndDocumentType_IdAndIdNotAndStatus(
			ref,
			request.documentTypeId(),
			oldRuleId,
			ConservationRuleStatus.VALIDE
		)) {
			throw duplicateReferenceException();
		}
		ConservationRule newRule = conservationRuleRepository.save(buildValideRuleFromCreateRequest(request, ref, fd));
		migrateLinkedBoitesToRule(oldRule, newRule);
		oldRule.setStatus(ConservationRuleStatus.INVALIDE);
		conservationRuleRepository.save(oldRule);
		return toResponse(newRule);
	}

	private ConservationRule buildValideRuleFromCreateRequest(
		CreateConservationRuleRequest request,
		String ref,
		FinalDecision fd
	) {
		ConservationRule rule = new ConservationRule();
		rule.setReference(ref);
		rule.setFinalDecision(fd);
		rule.setActiveUnknown(Boolean.TRUE.equals(request.activeUnknown()));
		rule.setActiveYears(rule.isActiveUnknown() ? null : request.activeYears());
		applySemiActiveForDecision(rule, fd, request.semiActiveUnknown(), request.semiActiveYears());
		rule.setStatus(ConservationRuleStatus.VALIDE);
		applyDocumentType(rule, request.documentTypeId());
		return rule;
	}

	@Transactional
	public ConservationRuleResponse update(Authentication authentication, Long id, UpdateConservationRuleRequest request) {
		requireAdmin(authentication);
		ConservationRule rule = conservationRuleRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Règle de conservation introuvable."));
		String ref = request.reference().trim();
		FinalDecision fd = request.finalDecision();
		validateDurations(fd, request.activeUnknown(), request.activeYears(), request.semiActiveUnknown(), request.semiActiveYears());
		if (ref.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_REFERENCE", "Référence obligatoire.");
		}
		if (conservationRuleRepository.existsByReferenceIgnoreCaseAndDocumentType_IdAndIdNotAndStatus(ref, request.documentTypeId(), id, ConservationRuleStatus.VALIDE)) {
			throw new ApiException(
				HttpStatus.CONFLICT,
				"DUPLICATE_REFERENCE",
				"Une règle valide avec cette référence existe déjà pour ce type de document.");
		}
		rule.setReference(ref);
		rule.setFinalDecision(fd);
		rule.setActiveUnknown(Boolean.TRUE.equals(request.activeUnknown()));
		rule.setActiveYears(rule.isActiveUnknown() ? null : request.activeYears());
		applySemiActiveForDecision(rule, fd, request.semiActiveUnknown(), request.semiActiveYears());
		applyDocumentType(rule, request.documentTypeId());
		if (rule.getStatus() == ConservationRuleStatus.VALIDE) {
			ensureAtMostOneValideRulePerDocumentType(rule.getDocumentType().getId(), rule.getId());
		}
		ConservationRule saved = conservationRuleRepository.save(rule);
		if (!saved.isSemiActiveUnknown()) {
			clearSemiActifSnoozeForLinkedBoites(saved);
		}
		return toResponse(saved);
	}

	private void clearSemiActifSnoozeForLinkedBoites(ConservationRule rule) {
		List<Boite> boites = boiteRepository.findLinkedToConservationRuleForInvalidationUpdate(
			rule.getId(),
			rule.getDocumentType().getId()
		);
		for (Boite b : boites) {
			b.setSemiActifAlerteAnneeAffichage(null);
		}
		if (!boites.isEmpty()) {
			boiteRepository.saveAll(boites);
		}
	}

	@Transactional(readOnly = true)
	public ConservationRuleInvalidationPreviewDto invalidationPreview(Authentication authentication, Long id) {
		requireAdmin(authentication);
		ConservationRule rule = requireValideRule(id);
		return buildInvalidationPreview(rule);
	}

	@Transactional
	public ConservationRuleResponse invalidateWithStrategy(
		Authentication authentication,
		Long id,
		InvalidateConservationRuleRequest request
	) {
		requireAdmin(authentication);
		ConservationRule rule = requireValideRule(id);
		if (request.boiteStrategy() != InvalidationBoiteStrategy.KEEP_ON_OLD_RULE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STRATEGY", "Stratégie d’invalidation non supportée.");
		}
		pinLinkedBoitesToRule(rule);
		rule.setStatus(ConservationRuleStatus.INVALIDE);
		return toResponse(conservationRuleRepository.save(rule));
	}

	@Transactional
	public ConservationRuleResponse updateStatus(Authentication authentication, Long id, UpdateConservationRuleStatusRequest request) {
		requireAdmin(authentication);
		ConservationRule rule = conservationRuleRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Règle de conservation introuvable."));
		if (request.status() == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Statut obligatoire.");
		}
		if (request.status() == ConservationRuleStatus.INVALIDE && rule.getStatus() == ConservationRuleStatus.VALIDE) {
			ConservationRuleInvalidationPreviewDto preview = buildInvalidationPreview(rule);
			if (preview.totalBoites() > 0) {
				throw new ApiException(
					HttpStatus.CONFLICT,
					"RULE_HAS_LINKED_BOITES",
					"Cette règle est liée à "
						+ preview.totalBoites()
						+ " boîte(s). Utilisez l’assistant d’invalidation (conserver l’ancienne règle ou créer une nouvelle)."
				);
			}
		}
		if (request.status() == ConservationRuleStatus.VALIDE) {
			ensureAtMostOneValideRulePerDocumentType(rule.getDocumentType().getId(), rule.getId());
		}
		rule.setStatus(request.status());
		return toResponse(conservationRuleRepository.save(rule));
	}

	private void validateDurations(
		FinalDecision finalDecision,
		Boolean activeUnknown,
		Integer activeYears,
		Boolean semiUnknown,
		Integer semiYears
	) {
		boolean au = Boolean.TRUE.equals(activeUnknown);
		if (!au && (activeYears == null || activeYears < 0)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ACTIVE_YEARS", "Indiquez un nombre d’années actives ≥ 0 ou cochez « durée inconnue ».");
		}
		if (finalDecision == FinalDecision.CONSERVER) {
			return;
		}
		boolean su = Boolean.TRUE.equals(semiUnknown);
		if (!su && (semiYears == null || semiYears < 0)) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"INVALID_SEMI_YEARS",
				"Pour Détruire ou Transférer : renseignez un nombre d’années semi-actives ≥ 0."
			);
		}
	}

	private void applySemiActiveForDecision(
		ConservationRule rule,
		FinalDecision finalDecision,
		Boolean semiActiveUnknown,
		Integer semiActiveYears
	) {
		if (finalDecision == FinalDecision.CONSERVER) {
			rule.setSemiActiveUnknown(false);
			rule.setSemiActiveYears(null);
			return;
		}
		boolean su = Boolean.TRUE.equals(semiActiveUnknown);
		rule.setSemiActiveUnknown(su);
		rule.setSemiActiveYears(su ? null : semiActiveYears);
	}

	private static ApiException duplicateReferenceException() {
		return new ApiException(
			HttpStatus.CONFLICT,
			"DUPLICATE_REFERENCE",
			"Une règle valide avec cette référence existe déjà pour ce type de document."
		);
	}

	/**
	 * Remplacement : la nouvelle règle reprend référence + type de l’ancienne encore valide (invalidée juste après).
	 */
	private ConservationRule requireReplacingRuleForCreate(Long replacingRuleId, String reference, Long documentTypeId) {
		ConservationRule replacing = conservationRuleRepository.findById(replacingRuleId)
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "REPLACING_RULE_NOT_FOUND", "Règle à remplacer introuvable."));
		if (replacing.getStatus() != ConservationRuleStatus.VALIDE) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"REPLACING_RULE_NOT_VALIDE",
				"Seule une règle encore valide peut être remplacée par ce flux."
			);
		}
		if (!replacing.getDocumentType().getId().equals(documentTypeId)) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"REPLACING_RULE_TYPE_MISMATCH",
				"La nouvelle règle doit avoir le même type de document que celle remplacée."
			);
		}
		if (!replacing.getReference().equalsIgnoreCase(reference.trim())) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"REPLACING_RULE_REFERENCE_MISMATCH",
				"En remplacement, la référence doit rester celle de la règle remplacée."
			);
		}
		return replacing;
	}

	private void applyDocumentType(ConservationRule rule, Long documentTypeId) {
		if (documentTypeId == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NO_DOCUMENT_TYPE", "Type de document obligatoire.");
		}
		DocumentType type = documentTypeRepository.findById(documentTypeId)
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_DOCUMENT_TYPE", "Type de document inconnu."));
		rule.setDocumentType(type);
	}

	/**
	 * Au plus une règle {@link ConservationRuleStatus#VALIDE} par type de document.
	 *
	 * @param excludeRuleId {@code null} en création ; sinon id de la règle courante (mise à jour / réactivation).
	 */
	private void ensureAtMostOneValideRulePerDocumentType(Long documentTypeId, Long excludeRuleId) {
		if (excludeRuleId == null) {
			if (conservationRuleRepository.countValideForDocumentType(documentTypeId) > 0) {
				String blockingRef = firstValideReferenceBlocking(documentTypeId);
				String detail = blockingRef != null
					? "Une règle valide existe déjà pour ce type de document (réf. « " + blockingRef + " »). "
						+ "Invalidez-la ou modifiez-la avant d’en créer une autre. Si vous ne la voyez pas, videz la recherche et affichez tous les statuts."
					: "Une règle valide existe déjà pour ce type de document. Modifiez la règle existante ou invalidez-la avant d’en ajouter une autre.";
				throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_DOCUMENT_TYPE", detail);
			}
		} else {
			if (conservationRuleRepository.countOtherValideForDocumentType(documentTypeId, excludeRuleId) > 0) {
				String blockingRef = firstOtherValideReferenceBlocking(documentTypeId, excludeRuleId);
				String detail = blockingRef != null
					? "Une autre règle valide existe déjà pour ce type de document (réf. « " + blockingRef + " »)."
					: "Une autre règle valide existe déjà pour ce type de document.";
				throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_DOCUMENT_TYPE", detail);
			}
		}
	}

	private String firstValideReferenceBlocking(Long documentTypeId) {
		var refs = conservationRuleRepository.findFirstValideReferenceForDocumentType(documentTypeId);
		return firstRefOrNull(refs);
	}

	private String firstOtherValideReferenceBlocking(Long documentTypeId, Long excludeRuleId) {
		var refs = conservationRuleRepository.findFirstOtherValideReferenceForDocumentType(documentTypeId, excludeRuleId);
		return firstRefOrNull(refs);
	}

	private static String firstRefOrNull(List<String> refs) {
		if (refs == null || refs.isEmpty()) {
			return null;
		}
		String r = refs.get(0);
		return r == null ? null : r.trim();
	}

	private Specification<ConservationRule> filterSpec(
		String q,
		ConservationRuleStatus status,
		FinalDecision finalDecision,
		DurationAlertFilter durationAlert
	) {
		return (root, query, cb) -> {
			if (ConservationRule.class.equals(query.getResultType())) {
				root.fetch("documentType", JoinType.LEFT);
			}
			List<Predicate> preds = new ArrayList<>();
			if (q != null && !q.isEmpty()) {
				String qLower = q.toLowerCase();
				String like = "%" + qLower + "%";
				var dtJoin = root.join("documentType", JoinType.LEFT);
				var dirJoin = dtJoin.join("direction", JoinType.LEFT);
				List<Predicate> searchOr = new ArrayList<>();
				searchOr.add(cb.like(cb.lower(root.get("reference")), like));
				searchOr.add(cb.and(
					cb.isNotNull(dirJoin.get("id")),
					cb.or(
						cb.like(cb.lower(dirJoin.get("id")), like),
						cb.like(cb.lower(dirJoin.get("label")), like)
					)
				));
				searchOr.add(cb.like(cb.lower(root.get("finalDecision").as(String.class)), like));
				addFinalDecisionFrenchPredicates(searchOr, cb, root, qLower);
				preds.add(cb.or(searchOr.toArray(Predicate[]::new)));
			}
			if (status != null) {
				preds.add(cb.equal(root.get("status"), status));
			}
			if (finalDecision != null) {
				preds.add(cb.equal(root.get("finalDecision"), finalDecision));
			}
			if (durationAlert != null) {
				// Durée active inconnue : gérée sur la page Règles, pas dans Alertes.
				Predicate noDurationPageAlert = cb.disjunction();
				preds.add(durationAlert == DurationAlertFilter.ONLY ? noDurationPageAlert : cb.conjunction());
			}
			return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(Predicate[]::new));
		};
	}

	private static void addFinalDecisionFrenchPredicates(
		List<Predicate> searchOr,
		jakarta.persistence.criteria.CriteriaBuilder cb,
		jakarta.persistence.criteria.Root<ConservationRule> root,
		String qLower
	) {
		if (qLower.contains("conserv")) {
			searchOr.add(cb.equal(root.get("finalDecision"), FinalDecision.CONSERVER));
		}
		if (qLower.contains("detru") || qLower.contains("détru") || qLower.contains("destru")) {
			searchOr.add(cb.equal(root.get("finalDecision"), FinalDecision.DETRUIRE));
		}
		if (qLower.contains("transf")) {
			searchOr.add(cb.equal(root.get("finalDecision"), FinalDecision.TRANSFERER));
		}
	}

	/** Plus d’alerte « règle » dans le hub Alertes (durée semi-active → alertes boîtes). */
	private static boolean hasDurationAlert(ConservationRule r) {
		return false;
	}

	private ConservationRule requireValideRule(Long id) {
		ConservationRule rule = conservationRuleRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RULE_NOT_FOUND", "Règle de conservation introuvable."));
		if (rule.getStatus() != ConservationRuleStatus.VALIDE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "RULE_NOT_VALIDE", "Seule une règle valide peut être invalidée ainsi.");
		}
		return rule;
	}

	private ConservationRuleInvalidationPreviewDto buildInvalidationPreview(ConservationRule rule) {
		Long ruleId = rule.getId();
		Long typeId = rule.getDocumentType().getId();
		String typeTitle = rule.getDocumentType().getTitle();
		List<Boite> boites = boiteRepository.findLinkedToConservationRuleForInvalidation(ruleId, typeId);
		Map<Long, String> numeroByBordereau = new LinkedHashMap<>();
		Map<Long, List<ConservationRuleLinkedBoiteDto>> boitesByBordereau = new LinkedHashMap<>();
		for (Boite b : boites) {
			Long bordereauId = b.getBordereau().getId();
			numeroByBordereau.putIfAbsent(bordereauId, b.getBordereau().getNumeroAffiche());
			boitesByBordereau.computeIfAbsent(bordereauId, ignored -> new ArrayList<>()).add(
				new ConservationRuleLinkedBoiteDto(b.getId(), b.getTitre(), b.getAnneeMin(), b.getAnneeMax(), b.getMetrageCm())
			);
		}
		List<ConservationRuleLinkedBordereauDto> bordereaux = new ArrayList<>();
		for (Map.Entry<Long, List<ConservationRuleLinkedBoiteDto>> e : boitesByBordereau.entrySet()) {
			bordereaux.add(
				new ConservationRuleLinkedBordereauDto(e.getKey(), numeroByBordereau.get(e.getKey()), List.copyOf(e.getValue()))
			);
		}
		return new ConservationRuleInvalidationPreviewDto(
			ruleId,
			rule.getReference(),
			typeId,
			typeTitle,
			boites.size(),
			bordereaux
		);
	}

	private void pinLinkedBoitesToRule(ConservationRule rule) {
		List<Boite> boites = boiteRepository.findLinkedToConservationRuleForInvalidationUpdate(
			rule.getId(),
			rule.getDocumentType().getId()
		);
		for (Boite b : boites) {
			b.setConservationRule(rule);
		}
		if (!boites.isEmpty()) {
			boiteRepository.saveAll(boites);
		}
	}

	private void migrateLinkedBoitesToRule(ConservationRule oldRule, ConservationRule newRule) {
		List<Boite> boites = boiteRepository.findLinkedToConservationRuleForInvalidationUpdate(
			oldRule.getId(),
			oldRule.getDocumentType().getId()
		);
		for (Boite b : boites) {
			b.setConservationRule(newRule);
		}
		if (!boites.isEmpty()) {
			boiteRepository.saveAll(boites);
		}
	}

	private ConservationRuleResponse toResponse(ConservationRule r) {
		DocumentType dt = r.getDocumentType();
		DocumentTypeMiniDto typeDto = new DocumentTypeMiniDto(dt.getId(), dt.getTitle());
		return new ConservationRuleResponse(
			r.getId(),
			r.getReference(),
			typeDto,
			r.isActiveUnknown(),
			r.getActiveYears(),
			r.isSemiActiveUnknown(),
			r.getSemiActiveYears(),
			r.getFinalDecision().name(),
			r.getStatus().name(),
			hasDurationAlert(r)
		);
	}

	private void requireAdmin(Authentication authentication) {
		authorization.requireAdmin(authentication);
	}
}
