package ommp.archives.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
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
	private final BoiteEtatService boiteEtatService;
	private final AuthorizationService authorization;

	public BoiteAlerteService(
		BoiteRepository boiteRepository,
		BoiteEtatRepository boiteEtatRepository,
		ConservationRuleRepository conservationRuleRepository,
		BoiteEtatService boiteEtatService,
		AuthorizationService authorization
	) {
		this.boiteRepository = boiteRepository;
		this.boiteEtatRepository = boiteEtatRepository;
		this.conservationRuleRepository = conservationRuleRepository;
		this.boiteEtatService = boiteEtatService;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public long countSemiActifInconnue(Authentication authentication) {
		authorization.requireAdmin(authentication);
		LocalDate today = LocalDate.now();
		return boiteRepository.count(semiActifAlerteSpec(null, null, today));
	}

	@Transactional(readOnly = true)
	public long countEcheanceDestructionTransfert(Authentication authentication) {
		authorization.requireAdmin(authentication);
		return boiteRepository.countEcheanceDestructionTransfertDue(
			BordereauStatut.AFFECTE,
			ConservationRuleStatus.VALIDE,
			List.of(FinalDecision.DETRUIRE, FinalDecision.TRANSFERER),
			BoiteEtatType.SEMI_ACTIF,
			LocalDate.now().getYear()
		);
	}

	@Transactional(readOnly = true)
	public Page<BoiteSemiActifAlerteResponse> listSemiActifInconnue(
		Authentication authentication,
		String q,
		Pageable pageable
	) {
		authorization.requireAdmin(authentication);
		String qTrim = q == null || q.isBlank() ? null : q.trim();
		LocalDate today = LocalDate.now();
		Specification<Boite> spec = semiActifAlerteSpec(null, qTrim, today);
		return boiteRepository.findAll(spec, pageable).map(box -> toSemiActifAlerteResponse(box, today));
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
		ConservationRule rule = box.getConservationRule();
		if (rule == null
			|| rule.getStatus() != ConservationRuleStatus.VALIDE
			|| !rule.isSemiActiveUnknown()
			|| (rule.getFinalDecision() != FinalDecision.DETRUIRE && rule.getFinalDecision() != FinalDecision.TRANSFERER)) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"BOITE_NOT_IN_SEMI_ALERT",
				"Cette boîte n’est pas concernée par une alerte de durée semi-active inconnue."
			);
		}
		if (box.getBordereau().getStatut() != BordereauStatut.AFFECTE) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"BORDEREAU_NOT_AFFECTE",
				"Seules les boîtes de bordereaux affectés peuvent être reportées."
			);
		}
		box.setSemiActifAlerteAnneeAffichage(annee);
		boiteRepository.save(box);
		return toSemiActifAlerteResponse(box, LocalDate.now());
	}

	@Transactional(readOnly = true)
	public Page<BoiteEcheanceAlerteResponse> listEcheanceDestructionTransfert(
		Authentication authentication,
		String q,
		Pageable pageable
	) {
		authorization.requireAdmin(authentication);
		String qTrim = q == null || q.isBlank() ? null : q.trim().toLowerCase();
		LocalDate today = LocalDate.now();
		int currentYear = today.getYear();

		Specification<Boite> spec = echeanceDestructionTransfertBaseSpec(null, true)
			.and(echeanceDueYearSpec(currentYear));
		if (qTrim != null) {
			spec = spec.and(echeanceSearchSpec(qTrim));
		}

		Pageable sorted = PageRequest.of(
			pageable.getPageNumber(),
			pageable.getPageSize(),
			Sort.by(Sort.Order.asc("anneeMax"), Sort.Order.asc("titre"))
		);
		return boiteRepository.findAll(spec, sorted).map(box -> toEcheanceAlerteResponse(box, today).orElseThrow());
	}

	@Transactional
	public BoiteEcheanceAlerteResponse approuverDestructionTransfert(Authentication authentication, Long boiteId) {
		authorization.requireAdmin(authentication);
		Boite box = boiteRepository.findById(boiteId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOITE_NOT_FOUND", "Boîte introuvable."));
		LocalDate today = LocalDate.now();
		Optional<BoiteEcheanceAlerteResponse> echeance = toEcheanceAlerteResponse(box, today);
		if (echeance.isPresent()) {
			executeApprobationDestructionTransfert(box, authentication);
			return echeance.get();
		}
		if (!isSemiActifInconnueActionDue(box, today)) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"BOITE_NOT_IN_ECHEANCE_ALERT",
				"Cette boîte n’est pas en alerte d’échéance destruction / transfert."
			);
		}
		ConservationRule rule = box.getConservationRule();
		int semiActiveYears = resolveSemiActiveYearsForApproval(box, today);
		ConservationRule updatedRule = concludeSemiActiveDurationOnRule(rule, semiActiveYears);
		box.setConservationRule(updatedRule);
		int anneeFinPrevue = box.getAnneeMax() + semiActiveYears;
		executeApprobationDestructionTransfert(box, authentication);
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

	private void executeApprobationDestructionTransfert(Boite box, Authentication authentication) {
		BoiteEtat courant = box.getEtatCourant();
		if (courant != null && courant.getTypeEtat() != BoiteEtatType.SEMI_ACTIF) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"BOITE_ALREADY_APPROVED",
				"Cette boîte a déjà été validée pour destruction ou transfert."
			);
		}
		ConservationRule rule = box.getConservationRule();
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

	private boolean matchesEcheanceSearch(BoiteEcheanceAlerteResponse row, String qTrim) {
		if (qTrim == null || qTrim.isEmpty()) {
			return true;
		}
		return containsIgnoreCase(row.boiteTitre(), qTrim)
			|| containsIgnoreCase(row.numeroBordereau(), qTrim)
			|| containsIgnoreCase(row.regleReference(), qTrim)
			|| containsIgnoreCase(row.documentTypeTitle(), qTrim);
	}

	private static boolean containsIgnoreCase(String value, String q) {
		return value != null && value.toLowerCase().contains(q);
	}

	private Optional<BoiteEcheanceAlerteResponse> toEcheanceAlerteResponse(Boite box, LocalDate today) {
		ConservationRule rule = box.getConservationRule();
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

	private Specification<Boite> echeanceDueYearSpec(int currentYear) {
		return (root, query, cb) -> {
			Join<Boite, ConservationRule> rule = root.join("conservationRule", JoinType.INNER);
			return cb.lessThanOrEqualTo(
				cb.sum(root.get("anneeMax"), rule.get("semiActiveYears")),
				(long) currentYear
			);
		};
	}

	private Specification<Boite> echeanceSearchSpec(String qTrim) {
		return (root, query, cb) -> {
			String like = "%" + qTrim + "%";
			Join<Boite, Bordereau> br = root.join("bordereau", JoinType.INNER);
			Join<Boite, ConservationRule> rule = root.join("conservationRule", JoinType.INNER);
			Join<Boite, DocumentType> dt = root.join("documentType", JoinType.INNER);
			return cb.or(
				cb.like(cb.lower(root.get("titre")), like),
				cb.like(cb.lower(br.get("numeroAffiche")), like),
				cb.like(cb.lower(rule.get("reference")), like),
				cb.like(cb.lower(dt.get("title")), like)
			);
		};
	}

	private Specification<Boite> echeanceDestructionTransfertBaseSpec(String userDirectionId, boolean fetchAssociations) {
		return (root, query, cb) -> {
			if (fetchAssociations && Boite.class.equals(query.getResultType())) {
				root.fetch("bordereau", JoinType.INNER);
				root.fetch("conservationRule", JoinType.INNER);
				root.fetch("documentType", JoinType.INNER);
				root.fetch("etatCourant", JoinType.INNER);
			}
			List<Predicate> preds = new ArrayList<>();
			Join<Boite, Bordereau> br = root.join("bordereau", JoinType.INNER);
			Join<Boite, ConservationRule> rule = root.join("conservationRule", JoinType.INNER);
			Join<Boite, DocumentType> dt = root.join("documentType", JoinType.INNER);

			preds.add(cb.equal(br.get("statut"), BordereauStatut.AFFECTE));
			preds.add(cb.equal(rule.get("status"), ConservationRuleStatus.VALIDE));
			preds.add(rule.get("finalDecision").in(FinalDecision.DETRUIRE, FinalDecision.TRANSFERER));
			preds.add(cb.isFalse(rule.get("semiActiveUnknown")));
			preds.add(cb.isNotNull(rule.get("semiActiveYears")));
			ajouterFiltreEtatCourantSemiActif(root, cb, preds);

			if (userDirectionId != null && !userDirectionId.isBlank()) {
				Join<DocumentType, Direction> dir = dt.join("direction", JoinType.LEFT);
				preds.add(cb.equal(dir.get("id"), userDirectionId));
			}

			return cb.and(preds.toArray(Predicate[]::new));
		};
	}

	/** {@code BOITES.ETAT_COURANT_ID} → {@code ETATS.TYPE_ETAT = SEMI_ACTIF}. */
	private static void ajouterFiltreEtatCourantSemiActif(Root<Boite> root, CriteriaBuilder cb, List<Predicate> preds) {
		Join<Boite, BoiteEtat> etatCourant = root.join("etatCourant", JoinType.INNER);
		preds.add(cb.equal(etatCourant.get("typeEtat"), BoiteEtatType.SEMI_ACTIF));
	}

	private BoiteSemiActifAlerteResponse toSemiActifAlerteResponse(Boite box, LocalDate today) {
		ConservationRule rule = box.getConservationRule();
		Bordereau br = box.getBordereau();
		DocumentType dt = box.getDocumentType();
		Integer anneeAffichage = box.getSemiActifAlerteAnneeAffichage();
		boolean actionEcheance = isSemiActifInconnueActionDue(box, today);
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

	private static boolean isSemiActifInconnueActionDue(Boite box, LocalDate today) {
		Integer anneeAffichage = box.getSemiActifAlerteAnneeAffichage();
		if (anneeAffichage == null || anneeAffichage > today.getYear()) {
			return false;
		}
		ConservationRule rule = box.getConservationRule();
		if (rule == null
			|| rule.getStatus() != ConservationRuleStatus.VALIDE
			|| !rule.isSemiActiveUnknown()
			|| (rule.getFinalDecision() != FinalDecision.DETRUIRE && rule.getFinalDecision() != FinalDecision.TRANSFERER)) {
			return false;
		}
		Bordereau br = box.getBordereau();
		if (br == null || br.getStatut() != BordereauStatut.AFFECTE) {
			return false;
		}
		BoiteEtat courant = box.getEtatCourant();
		return courant != null && courant.getTypeEtat() == BoiteEtatType.SEMI_ACTIF;
	}

	private Specification<Boite> semiActifAlerteSpec(String userDirectionId, String q, LocalDate today) {
		return (root, query, cb) -> {
			if (Boite.class.equals(query.getResultType())) {
				root.fetch("bordereau", JoinType.INNER);
				root.fetch("documentType", JoinType.INNER);
				root.fetch("conservationRule", JoinType.INNER);
			}
			List<Predicate> preds = new ArrayList<>();
			Join<Boite, Bordereau> br = root.join("bordereau", JoinType.INNER);
			Join<Boite, ConservationRule> rule = root.join("conservationRule", JoinType.INNER);
			Join<Boite, DocumentType> dt = root.join("documentType", JoinType.INNER);

			preds.add(cb.equal(br.get("statut"), BordereauStatut.AFFECTE));
			preds.add(cb.equal(rule.get("status"), ConservationRuleStatus.VALIDE));
			preds.add(rule.get("finalDecision").in(FinalDecision.DETRUIRE, FinalDecision.TRANSFERER));
			preds.add(cb.isTrue(rule.get("semiActiveUnknown")));
			ajouterFiltreEtatCourantSemiActif(root, cb, preds);
			int currentYear = today.getYear();
			preds.add(cb.or(
				cb.isNull(root.get("semiActifAlerteAnneeAffichage")),
				cb.le(root.get("semiActifAlerteAnneeAffichage"), currentYear)
			));

			if (userDirectionId != null && !userDirectionId.isBlank()) {
				Join<DocumentType, Direction> dir = dt.join("direction", JoinType.LEFT);
				preds.add(cb.equal(dir.get("id"), userDirectionId));
			}

			if (q != null && !q.isEmpty()) {
				String like = "%" + q.toLowerCase() + "%";
				preds.add(cb.or(
					cb.like(cb.lower(root.get("titre")), like),
					cb.like(cb.lower(br.get("numeroAffiche")), like),
					cb.like(cb.lower(rule.get("reference")), like),
					cb.like(cb.lower(dt.get("title")), like)
				));
			}

			return cb.and(preds.toArray(Predicate[]::new));
		};
	}
}
