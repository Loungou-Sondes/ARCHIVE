package ommp.archives.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import ommp.archives.dto.boite.BoiteAlerteArchivesRow;
import ommp.archives.dto.boite.BoiteEcheanceAlerteResponse;
import ommp.archives.dto.boite.BoiteSemiActifAlerteResponse;
import ommp.archives.entity.Boite;
import ommp.archives.entity.BoiteEtat;
import ommp.archives.entity.BoiteEtatAction;
import ommp.archives.entity.BoiteEtatType;
import ommp.archives.entity.Bordereau;
import ommp.archives.entity.BordereauStatut;
import ommp.archives.entity.ConservationRule;
import ommp.archives.entity.ConservationRuleStatus;
import ommp.archives.entity.Direction;
import ommp.archives.entity.DocumentType;
import ommp.archives.entity.FinalDecision;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.BoiteRepository;
import ommp.archives.repository.BoiteEtatRepository;
import ommp.archives.repository.ConservationRuleRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class BoiteAlerteService {

	private final BoiteRepository boiteRepository;
	private final BoiteEtatRepository boiteEtatRepository;
	private final ConservationRuleRepository conservationRuleRepository;
	private final ConservationRuleResolver conservationRuleResolver;
	private final BoiteEtatService boiteEtatService;
	private final AuthorizationService authorization;

	public BoiteAlerteService(
		BoiteRepository boiteRepository,
		BoiteEtatRepository boiteEtatRepository,
		ConservationRuleRepository conservationRuleRepository,
		ConservationRuleResolver conservationRuleResolver,
		BoiteEtatService boiteEtatService,
		AuthorizationService authorization
	) {
		this.boiteRepository = boiteRepository;
		this.boiteEtatRepository = boiteEtatRepository;
		this.conservationRuleRepository = conservationRuleRepository;
		this.conservationRuleResolver = conservationRuleResolver;
		this.boiteEtatService = boiteEtatService;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public long countSemiActifInconnue(Authentication authentication) {
		authorization.requireAdmin(authentication);
		return collectSemiActifAlertes(LocalDate.now(), null).size();
	}

	@Transactional(readOnly = true)
	public long countEcheanceDestructionTransfert(Authentication authentication) {
		authorization.requireAdmin(authentication);
		return collectEcheanceAlertes(LocalDate.now(), null).size();
	}

	@Transactional(readOnly = true)
	public Page<BoiteAlerteArchivesRow> listAlertesArchives(
		Authentication authentication,
		String q,
		Pageable pageable
	) {
		authorization.requireAdmin(authentication);
		String qTrim = normalizeSearch(q);
		LocalDate today = LocalDate.now();

		List<BoiteAlerteArchivesRow> semiRows = collectSemiActifAlertes(today, qTrim).stream()
			.map(box -> BoiteAlerteArchivesRow.semiActif(
				toSemiActifAlerteResponse(box, resolveEffectiveRule(box), today)))
			.toList();
		List<BoiteAlerteArchivesRow> echRows = collectEcheanceAlertes(today, qTrim).stream()
			.map(box -> BoiteAlerteArchivesRow.echeance(
				toEcheanceAlerteResponse(box, resolveEffectiveRule(box), today).orElseThrow()))
			.toList();

		List<BoiteAlerteArchivesRow> all = new ArrayList<>(semiRows.size() + echRows.size());
		all.addAll(semiRows);
		all.addAll(echRows);

		int page = Math.max(0, pageable.getPageNumber());
		int size = pageable.getPageSize() > 0 ? pageable.getPageSize() : 12;
		int from = Math.min(page * size, all.size());
		int to = Math.min(from + size, all.size());
		return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), all.size());
	}

	@Transactional
	public BoiteSemiActifAlerteResponse reporterSemiActifAlerte(
		Authentication authentication,
		Long boiteId,
		int annee
	) {
		authorization.requireAdmin(authentication);
		int currentYear = LocalDate.now().getYear();
		int minYear = currentYear + 1;
		int maxYear = currentYear + 50;
		if (annee < minYear || annee > maxYear) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"ANNEE_AFFICHAGE_INVALIDE",
				"L'année d'affichage doit être comprise entre " + minYear + " et " + maxYear + "."
			);
		}
		Boite box = boiteRepository.findById(boiteId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOITE_NOT_FOUND", "Boîte introuvable."));
		ConservationRule rule = requireSemiActifInconnueRule(box);
		syncConservationRuleLink(box, rule);
		if (box.getBordereau().getStatut() != BordereauStatut.AFFECTE) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"BORDEREAU_NOT_AFFECTE",
				"Seules les boîtes de bordereaux affectés peuvent être reportées."
			);
		}
		box.setSemiActifAlerteAnneeAffichage(annee);
		boiteRepository.save(box);
		return toSemiActifAlerteResponse(box, rule, LocalDate.now());
	}

	@Transactional
	public BoiteEcheanceAlerteResponse approuverDestructionTransfert(Authentication authentication, Long boiteId) {
		authorization.requireAdmin(authentication);
		Boite box = boiteRepository.findById(boiteId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOITE_NOT_FOUND", "Boîte introuvable."));
		LocalDate today = LocalDate.now();
		ConservationRule rule = resolveEffectiveRule(box);
		Optional<BoiteEcheanceAlerteResponse> echeance = toEcheanceAlerteResponse(box, rule, today);
		if (echeance.isPresent()) {
			syncConservationRuleLink(box, rule);
			executeApprobationDestructionTransfert(box, rule, authentication);
			return echeance.get();
		}
		if (!isSemiActifInconnueActionDue(box, rule, today)) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"BOITE_NOT_IN_ECHEANCE_ALERT",
				"Cette boîte n’est pas en alerte d’échéance destruction / transfert."
			);
		}
		int semiActiveYears = resolveSemiActiveYearsForApproval(box, today);
		ConservationRule updatedRule = concludeSemiActiveDurationOnRule(rule, semiActiveYears);
		box.setConservationRule(updatedRule);
		int anneeFinPrevue = box.getAnneeMax() + semiActiveYears;
		executeApprobationDestructionTransfert(box, updatedRule, authentication);
		Bordereau br = box.getBordereau();
		DocumentType dt = box.getDocumentType();
		return new BoiteEcheanceAlerteResponse(
			box.getId(),
			box.getTitre(),
			br.getId(),
			br.getNumeroAffiche(),
			updatedRule.getId(),
			updatedRule.getReference(),
			dt == null ? null : dt.getTitle(),
			updatedRule.getFinalDecision().name(),
			anneeFinPrevue
		);
	}

	private List<Boite> collectSemiActifAlertes(LocalDate today, String qTrim) {
		return loadAlerteCandidates(null, qTrim).stream()
			.filter(box -> matchesSemiActifAlerte(box, resolveEffectiveRule(box), today, qTrim))
			.sorted(Comparator.comparing(Boite::getTitre, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private List<Boite> collectEcheanceAlertes(LocalDate today, String qTrim) {
		return loadAlerteCandidates(null, qTrim).stream()
			.filter(box -> matchesEcheanceAlerte(box, resolveEffectiveRule(box), today, qTrim))
			.sorted(Comparator
				.comparingInt(Boite::getAnneeMax)
				.thenComparing(Boite::getTitre, String.CASE_INSENSITIVE_ORDER))
			.toList();
	}

	private List<Boite> loadAlerteCandidates(String userDirectionId, String qTrim) {
		return boiteRepository.findAll(alerteCandidateSpec(userDirectionId, qTrim));
	}

	/**
	 * Règle applicable à la boîte :
	 * <ol>
	 *   <li>règle liée si encore {@link ConservationRuleStatus#VALIDE} ;</li>
	 *   <li>sinon règle valide courante du type de document ;</li>
	 *   <li>sinon règle liée invalidée (boîte figée sur l’ancienne politique).</li>
	 * </ol>
	 */
	private ConservationRule resolveEffectiveRule(Boite box) {
		ConservationRule linked = box.getConservationRule();
		if (linked != null && linked.getStatus() == ConservationRuleStatus.VALIDE) {
			return linked;
		}
		DocumentType documentType = box.getDocumentType();
		if (documentType != null && documentType.getId() != null) {
			Optional<ConservationRule> valide = conservationRuleResolver.findValideRuleForDocumentType(documentType.getId());
			if (valide.isPresent()) {
				return valide.get();
			}
		}
		if (linked != null && isDestructionOuTransfert(linked)) {
			return linked;
		}
		return null;
	}

	private void syncConservationRuleLink(Boite box, ConservationRule rule) {
		if (rule == null) {
			return;
		}
		ConservationRule linked = box.getConservationRule();
		if (linked == null || !rule.getId().equals(linked.getId())) {
			box.setConservationRule(rule);
			boiteRepository.save(box);
		}
	}

	private ConservationRule requireSemiActifInconnueRule(Boite box) {
		ConservationRule rule = resolveEffectiveRule(box);
		if (rule == null
			|| !rule.isSemiActiveUnknown()
			|| (rule.getFinalDecision() != FinalDecision.DETRUIRE && rule.getFinalDecision() != FinalDecision.TRANSFERER)) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"BOITE_NOT_IN_SEMI_ALERT",
				"Cette boîte n’est pas concernée par une alerte de durée semi-active inconnue."
			);
		}
		return rule;
	}

	private boolean matchesSemiActifAlerte(Boite box, ConservationRule rule, LocalDate today, String qTrim) {
		if (!isSemiActifEtat(box) || !isBordereauAffecte(box)) {
			return false;
		}
		if (!isDestructionOuTransfert(rule) || rule == null || !rule.isSemiActiveUnknown()) {
			return false;
		}
		Integer anneeAffichage = box.getSemiActifAlerteAnneeAffichage();
		if (anneeAffichage != null && anneeAffichage > today.getYear()) {
			return false;
		}
		return matchesAlerteSearch(box, rule, qTrim);
	}

	private boolean matchesEcheanceAlerte(Boite box, ConservationRule rule, LocalDate today, String qTrim) {
		if (!isSemiActifEtat(box) || !isBordereauAffecte(box)) {
			return false;
		}
		if (rule == null || !isDestructionOuTransfert(rule) || rule.isSemiActiveUnknown()) {
			return false;
		}
		if (!ConservationDueDateCalculator.isDueForDestructionTransfertAlert(box, rule, today)) {
			return false;
		}
		return matchesAlerteSearch(box, rule, qTrim);
	}

	private static boolean isBordereauAffecte(Boite box) {
		Bordereau br = box.getBordereau();
		return br != null && br.getStatut() == BordereauStatut.AFFECTE;
	}

	private static boolean isSemiActifEtat(Boite box) {
		BoiteEtat courant = box.getEtatCourant();
		return courant != null && courant.getTypeEtat() == BoiteEtatType.SEMI_ACTIF;
	}

	private static boolean isDestructionOuTransfert(ConservationRule rule) {
		if (rule == null) {
			return false;
		}
		FinalDecision fd = rule.getFinalDecision();
		return fd == FinalDecision.DETRUIRE || fd == FinalDecision.TRANSFERER;
	}

	private boolean matchesAlerteSearch(Boite box, ConservationRule rule, String qTrim) {
		if (qTrim == null || qTrim.isEmpty()) {
			return true;
		}
		String qLower = qTrim.toLowerCase();
		DocumentType dt = box.getDocumentType();
		Bordereau br = box.getBordereau();
		return containsIgnoreCase(box.getTitre(), qLower)
			|| containsIgnoreCase(br == null ? null : br.getNumeroAffiche(), qLower)
			|| containsIgnoreCase(dt == null ? null : dt.getTitle(), qLower)
			|| containsIgnoreCase(rule.getReference(), qLower);
	}

	private static String normalizeSearch(String q) {
		if (q == null || q.isBlank()) {
			return null;
		}
		return q.trim();
	}

	private int resolveSemiActiveYearsForApproval(Boite box, LocalDate today) {
		LocalDate start = boiteEtatRepository
			.findEarliestDateByBoiteIdAndType(box.getId(), BoiteEtatType.SEMI_ACTIF)
			.orElseGet(() -> {
				BoiteEtat courant = box.getEtatCourant();
				return courant != null ? courant.getDateEtat() : today;
			});
		return Math.max(0, today.getYear() - start.getYear());
	}

	private ConservationRule concludeSemiActiveDurationOnRule(ConservationRule rule, int semiActiveYears) {
		if (rule == null) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"NO_CONSERVATION_RULE",
				"Aucune règle de conservation n’est liée à cette boîte."
			);
		}
		if (!rule.isSemiActiveUnknown()) {
			return rule;
		}
		rule.setSemiActiveUnknown(false);
		rule.setSemiActiveYears(semiActiveYears);
		return conservationRuleRepository.save(rule);
	}

	private void executeApprobationDestructionTransfert(
		Boite box,
		ConservationRule rule,
		Authentication authentication
	) {
		BoiteEtat courant = box.getEtatCourant();
		if (courant != null && courant.getTypeEtat() != BoiteEtatType.SEMI_ACTIF) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"BOITE_ALREADY_APPROVED",
				"Cette boîte a déjà été validée pour destruction ou transfert."
			);
		}
		BoiteEtatAction action = rule != null && rule.getFinalDecision() == FinalDecision.TRANSFERER
			? BoiteEtatAction.APPROBATION_TRANSFERT
			: BoiteEtatAction.APPROBATION_DESTRUCTION;
		boiteEtatService.recordState(
			box,
			action,
			authentication.getName(),
			"Approbation échéance destruction / transfert"
		);
	}

	private static boolean containsIgnoreCase(String value, String q) {
		return value != null && value.toLowerCase().contains(q);
	}

	private Optional<BoiteEcheanceAlerteResponse> toEcheanceAlerteResponse(
		Boite box,
		ConservationRule rule,
		LocalDate today
	) {
		Optional<Integer> echeanceYearOpt = ConservationDueDateCalculator.computeEcheanceYear(box, rule);
		if (echeanceYearOpt.isEmpty()
			|| !ConservationDueDateCalculator.isDueForDestructionTransfertAlert(box, rule, today)) {
			return Optional.empty();
		}
		int anneeEcheance = echeanceYearOpt.get();
		Bordereau br = box.getBordereau();
		if (br == null) {
			return Optional.empty();
		}
		DocumentType dt = box.getDocumentType();
		return Optional.of(new BoiteEcheanceAlerteResponse(
			box.getId(),
			box.getTitre(),
			br.getId(),
			br.getNumeroAffiche(),
			rule == null ? null : rule.getId(),
			rule == null ? null : rule.getReference(),
			dt == null ? null : dt.getTitle(),
			rule == null ? null : rule.getFinalDecision().name(),
			anneeEcheance
		));
	}

	private Specification<Boite> alerteCandidateSpec(String userDirectionId, String q) {
		return (root, query, cb) -> {
			if (Boite.class.equals(query.getResultType())) {
				root.fetch("bordereau", JoinType.INNER);
				root.fetch("documentType", JoinType.INNER);
				root.fetch("etatCourant", JoinType.INNER);
				root.fetch("conservationRule", JoinType.LEFT);
			}

			List<Predicate> preds = new ArrayList<>();
			Join<Boite, Bordereau> br = root.join("bordereau", JoinType.INNER);
			Join<Boite, DocumentType> dt = root.join("documentType", JoinType.INNER);

			preds.add(cb.equal(br.get("statut"), BordereauStatut.AFFECTE));
			ajouterFiltreEtatCourantSemiActif(root, cb, preds);

			if (userDirectionId != null && !userDirectionId.isBlank()) {
				Join<DocumentType, Direction> dir = dt.join("direction", JoinType.LEFT);
				preds.add(cb.equal(dir.get("id"), userDirectionId));
			}

			if (q != null && !q.isEmpty()) {
				String like = "%" + q.toLowerCase() + "%";
				preds.add(cb.or(
					cb.like(cb.lower(root.get("titre")), like),
					cb.like(cb.lower(br.get("numeroAffiche")), like),
					cb.like(cb.lower(dt.get("title")), like)
				));
			}

			return cb.and(preds.toArray(Predicate[]::new));
		};
	}

	/** {@code BOITES.ETAT_COURANT_ID} → {@code ETATS.TYPE_ETAT = SEMI_ACTIF}. */
	private static void ajouterFiltreEtatCourantSemiActif(Root<Boite> root, jakarta.persistence.criteria.CriteriaBuilder cb, List<Predicate> preds) {
		Join<Boite, BoiteEtat> etatCourant = root.join("etatCourant", JoinType.INNER);
		preds.add(cb.equal(etatCourant.get("typeEtat"), BoiteEtatType.SEMI_ACTIF));
	}

	private BoiteSemiActifAlerteResponse toSemiActifAlerteResponse(Boite box, ConservationRule rule, LocalDate today) {
		Bordereau br = box.getBordereau();
		DocumentType dt = box.getDocumentType();
		Integer anneeAffichage = box.getSemiActifAlerteAnneeAffichage();
		boolean actionEcheance = isSemiActifInconnueActionDue(box, rule, today);
		return new BoiteSemiActifAlerteResponse(
			box.getId(),
			box.getTitre(),
			br.getId(),
			br.getNumeroAffiche(),
			rule == null ? null : rule.getId(),
			rule == null ? null : rule.getReference(),
			dt == null ? null : dt.getId(),
			dt == null ? null : dt.getTitle(),
			rule == null ? null : rule.getFinalDecision().name(),
			anneeAffichage,
			actionEcheance
		);
	}

	private static boolean isSemiActifInconnueActionDue(Boite box, ConservationRule rule, LocalDate today) {
		Integer anneeAffichage = box.getSemiActifAlerteAnneeAffichage();
		if (anneeAffichage == null || anneeAffichage > today.getYear()) {
			return false;
		}
		if (!isSemiActifEtat(box) || !isBordereauAffecte(box)) {
			return false;
		}
		return rule != null && isDestructionOuTransfert(rule) && rule.isSemiActiveUnknown();
	}
}
