package ommp.archives.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import ommp.archives.dto.boite.ArchivesIntermediairesGroupedPageResponse;
import ommp.archives.dto.boite.BoiteArchivesIntermediaireItemDto;
import ommp.archives.dto.boite.BordereauArchivesIntermediairesGroupDto;
import ommp.archives.entity.Boite;
import ommp.archives.entity.BoiteEtat;
import ommp.archives.entity.BoiteEtatType;
import ommp.archives.entity.Bordereau;
import ommp.archives.entity.BordereauStatut;
import ommp.archives.entity.ConservationRule;
import ommp.archives.entity.Direction;
import ommp.archives.entity.DocumentType;
import ommp.archives.entity.Emplacement;
import ommp.archives.entity.TypeEmp;
import ommp.archives.repository.BoiteRepository;
import ommp.archives.repository.BordereauRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class BoiteArchivesIntermediairesService {

	private final BoiteRepository boiteRepository;
	private final BordereauRepository bordereauRepository;
	private final EmplacementOccupationService emplacementOccupationService;
	private final AuthorizationService authorization;

	public BoiteArchivesIntermediairesService(
		BoiteRepository boiteRepository,
		BordereauRepository bordereauRepository,
		EmplacementOccupationService emplacementOccupationService,
		AuthorizationService authorization
	) {
		this.boiteRepository = boiteRepository;
		this.bordereauRepository = bordereauRepository;
		this.emplacementOccupationService = emplacementOccupationService;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public ArchivesIntermediairesGroupedPageResponse listGrouped(
		Authentication authentication,
		String q,
		Pageable pageable
	) {
		authorization.requireAdmin(authentication);
		String qTrim = q == null || q.isBlank() ? null : q.trim().toLowerCase();
		Specification<Boite> spec = archivesIntermediairesSpec(null, qTrim);

		long totalBoites = boiteRepository.count(spec);
		Specification<Bordereau> bordereauSpec = archivesIntermediairesBordereauSpec(qTrim);
		Page<Bordereau> bordereauPage = bordereauRepository.findAll(
			bordereauSpec,
			PageRequest.of(
				pageable.getPageNumber(),
				pageable.getPageSize(),
				resolveBordereauSort(pageable)
			)
		);

		List<Long> bordereauIds = bordereauPage.getContent().stream().map(Bordereau::getId).toList();
		if (bordereauIds.isEmpty()) {
			return new ArchivesIntermediairesGroupedPageResponse(
				List.of(),
				0,
				totalBoites,
				bordereauPage.getTotalPages(),
				bordereauPage.getNumber(),
				bordereauPage.getSize()
			);
		}

		Specification<Boite> pageSpec = spec.and(
			(root, query, cb) -> root.get("bordereau").get("id").in(bordereauIds)
		);
		List<Boite> boxes = boiteRepository.findAll(
			pageSpec,
			Sort.by(
				Sort.Order.desc("bordereau.dateTransfert"),
				Sort.Order.desc("etatCourant.dateEtat"),
				Sort.Order.asc("titre")
			)
		);

		Map<Long, BordereauArchivesIntermediairesGroupDto> groupsById = new LinkedHashMap<>();
		for (Bordereau br : bordereauPage.getContent()) {
			groupsById.put(br.getId(), null);
		}
		for (BordereauArchivesIntermediairesGroupDto group : buildGroups(boxes)) {
			groupsById.put(group.bordereauId(), group);
		}
		List<BordereauArchivesIntermediairesGroupDto> pageContent = bordereauIds.stream()
			.map(groupsById::get)
			.filter(g -> g != null)
			.toList();

		return new ArchivesIntermediairesGroupedPageResponse(
			pageContent,
			bordereauPage.getTotalElements(),
			totalBoites,
			bordereauPage.getTotalPages(),
			bordereauPage.getNumber(),
			bordereauPage.getSize()
		);
	}

	private Sort resolveBordereauSort(Pageable pageable) {
		Sort sort = pageable == null ? Sort.unsorted() : pageable.getSort();
		Sort.Order dateOrder = sort.getOrderFor("dateTransfert");
		if (dateOrder != null) {
			return Sort.by(dateOrder);
		}
		Sort.Order anneeOrder = sort.getOrderFor("anneeNumero");
		if (anneeOrder != null) {
			return Sort.by(anneeOrder, Sort.Order.asc("numeroAffiche"));
		}
		return Sort.by(Sort.Order.desc("anneeNumero"), Sort.Order.desc("numeroAffiche"));
	}

	private List<BordereauArchivesIntermediairesGroupDto> buildGroups(List<Boite> boxes) {
		Map<Long, List<Boite>> byBordereau = new LinkedHashMap<>();
		for (Boite box : boxes) {
			Long brId = box.getBordereau().getId();
			byBordereau.computeIfAbsent(brId, k -> new ArrayList<>()).add(box);
		}
		return byBordereau.values().stream().map(this::toGroup).toList();
	}

	private static final Comparator<Boite> BOX_ITEM_ORDER = Comparator
		.<Boite, LocalDate>comparing(
			b -> b.getEtatCourant() == null ? LocalDate.MIN : b.getEtatCourant().getDateEtat(),
			Comparator.reverseOrder()
		)
		.thenComparing(Boite::getTitre, String.CASE_INSENSITIVE_ORDER);

	private BordereauArchivesIntermediairesGroupDto toGroup(List<Boite> boxes) {
		Bordereau br = boxes.get(0).getBordereau();
		Direction dir = br.getDirection();
		List<BoiteArchivesIntermediaireItemDto> items = boxes.stream()
			.sorted(BOX_ITEM_ORDER)
			.map(this::toItem)
			.toList();
		int enArchive = items.size();
		int total = br.getNombreBoites();
		if (total < enArchive) {
			total = (int) boiteRepository.countByBordereauId(br.getId());
		}
		return new BordereauArchivesIntermediairesGroupDto(
			br.getId(),
			br.getNumeroAffiche(),
			br.getDateTransfert().toString(),
			dir == null ? null : dir.getLabel(),
			br.getAgent().getUserName(),
			enArchive,
			total,
			items
		);
	}

	private BoiteArchivesIntermediaireItemDto toItem(Boite box) {
		BoiteEtat etat = box.getEtatCourant();
		DocumentType dt = box.getDocumentType();
		ConservationRule rule = box.getConservationRule();
		String emplacement = emplacementOccupationService.resolveDisplay(box, etat);
		return new BoiteArchivesIntermediaireItemDto(
			box.getId(),
			box.getTitre(),
			dt == null ? null : dt.getTitle(),
			etat == null ? null : etat.getDateEtat().toString(),
			emplacement,
			box.getAnneeMin(),
			box.getAnneeMax(),
			rule == null ? null : rule.getReference(),
			rule == null ? null : rule.getFinalDecision().name()
		);
	}

	private Specification<Bordereau> archivesIntermediairesBordereauSpec(String q) {
		return (root, query, cb) -> {
			Subquery<Long> sub = query.subquery(Long.class);
			Root<Boite> boxRoot = sub.from(Boite.class);
			Join<Boite, BoiteEtat> etatCourant = boxRoot.join("etatCourant", JoinType.INNER);
			Join<Boite, Bordereau> brJoin = boxRoot.join("bordereau", JoinType.INNER);
			Join<Boite, DocumentType> dt = boxRoot.join("documentType", JoinType.INNER);

			List<Predicate> boxPreds = new ArrayList<>();
			boxPreds.add(cb.equal(brJoin.get("statut"), BordereauStatut.AFFECTE));
			boxPreds.add(cb.equal(etatCourant.get("typeEtat"), BoiteEtatType.SEMI_ACTIF));

			Subquery<Integer> blocAssignSq = query.subquery(Integer.class);
			Root<Emplacement> empRoot = blocAssignSq.from(Emplacement.class);
			blocAssignSq.select(cb.literal(1));
			blocAssignSq.where(
				cb.equal(empRoot.get("boiteId"), boxRoot.get("id")),
				cb.equal(empRoot.get("typeEmp"), TypeEmp.BLOC)
			);
			boxPreds.add(cb.exists(blocAssignSq));

			if (q != null && !q.isEmpty()) {
				String like = "%" + q + "%";
				Subquery<Integer> plageSearchSq = query.subquery(Integer.class);
				Root<Emplacement> plageEmpRoot = plageSearchSq.from(Emplacement.class);
				plageSearchSq.select(cb.literal(1));
				plageSearchSq.where(
					cb.equal(plageEmpRoot.get("boiteId"), boxRoot.get("id")),
					cb.equal(plageEmpRoot.get("typeEmp"), TypeEmp.BLOC),
					cb.like(cb.lower(plageEmpRoot.get("numero")), like)
				);
				boxPreds.add(cb.or(
					cb.like(cb.lower(boxRoot.get("titre")), like),
					cb.like(cb.lower(brJoin.get("numeroAffiche")), like),
					cb.like(cb.lower(dt.get("title")), like),
					cb.exists(plageSearchSq),
					cb.like(cb.lower(etatCourant.get("positionId")), like)
				));
			}

			sub.select(brJoin.get("id"));
			sub.where(cb.and(boxPreds.toArray(Predicate[]::new)));
			return root.get("id").in(sub);
		};
	}

	private Specification<Boite> archivesIntermediairesSpec(String userDirectionId, String q) {
		return (root, query, cb) -> {
			if (Boite.class.equals(query.getResultType())) {
				var brFetch = root.fetch("bordereau", JoinType.INNER);
				brFetch.fetch("agent", JoinType.INNER);
				brFetch.fetch("direction", JoinType.LEFT);
				root.fetch("documentType", JoinType.INNER);
				root.fetch("conservationRule", JoinType.LEFT);
				root.fetch("etatCourant", JoinType.INNER);
			}

			List<Predicate> preds = new ArrayList<>();
			Join<Boite, Bordereau> br = root.join("bordereau", JoinType.INNER);
			Join<Boite, BoiteEtat> etatCourant = root.join("etatCourant", JoinType.INNER);
			Join<Boite, DocumentType> dt = root.join("documentType", JoinType.INNER);

			preds.add(cb.equal(br.get("statut"), BordereauStatut.AFFECTE));
			preds.add(cb.equal(etatCourant.get("typeEtat"), BoiteEtatType.SEMI_ACTIF));

			Subquery<Integer> blocAssignSq = query.subquery(Integer.class);
			Root<Emplacement> empRoot = blocAssignSq.from(Emplacement.class);
			blocAssignSq.select(cb.literal(1));
			blocAssignSq.where(
				cb.equal(empRoot.get("boiteId"), root.get("id")),
				cb.equal(empRoot.get("typeEmp"), TypeEmp.BLOC)
			);
			preds.add(cb.exists(blocAssignSq));

			if (userDirectionId != null && !userDirectionId.isBlank()) {
				Join<DocumentType, Direction> dir = dt.join("direction", JoinType.LEFT);
				preds.add(cb.equal(dir.get("id"), userDirectionId));
			}

			if (q != null && !q.isEmpty()) {
				String like = "%" + q.toLowerCase() + "%";
				Subquery<Integer> plageSearchSq = query.subquery(Integer.class);
				Root<Emplacement> plageEmpRoot = plageSearchSq.from(Emplacement.class);
				plageSearchSq.select(cb.literal(1));
				plageSearchSq.where(
					cb.equal(plageEmpRoot.get("boiteId"), root.get("id")),
					cb.equal(plageEmpRoot.get("typeEmp"), TypeEmp.BLOC),
					cb.like(cb.lower(plageEmpRoot.get("numero")), like)
				);
				preds.add(cb.or(
					cb.like(cb.lower(root.get("titre")), like),
					cb.like(cb.lower(br.get("numeroAffiche")), like),
					cb.like(cb.lower(dt.get("title")), like),
					cb.exists(plageSearchSq),
					cb.like(cb.lower(etatCourant.get("positionId")), like)
				));
			}

			return cb.and(preds.toArray(Predicate[]::new));
		};
	}
}
