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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import ommp.archives.dto.boite.BoiteHistoriqueItemDto;
import ommp.archives.dto.boite.BordereauHistoriqueGroupDto;
import ommp.archives.dto.boite.HistoriqueGroupedPageResponse;
import ommp.archives.entity.Boite;
import ommp.archives.entity.BoiteEtat;
import ommp.archives.entity.BoiteEtatType;
import ommp.archives.entity.Bordereau;
import ommp.archives.entity.ConservationRule;
import ommp.archives.entity.Direction;
import ommp.archives.entity.DocumentType;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.BoiteRepository;
import ommp.archives.repository.BordereauRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class BoiteHistoriqueService {

	private final BoiteRepository boiteRepository;
	private final BordereauRepository bordereauRepository;
	private final AuthorizationService authorization;

	public BoiteHistoriqueService(
		BoiteRepository boiteRepository,
		BordereauRepository bordereauRepository,
		AuthorizationService authorization
	) {
		this.boiteRepository = boiteRepository;
		this.bordereauRepository = bordereauRepository;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public HistoriqueGroupedPageResponse listGrouped(
		Authentication authentication,
		String q,
		String typeEtat,
		Pageable pageable
	) {
		authorization.requireAdmin(authentication);
		String qTrim = q == null || q.isBlank() ? null : q.trim().toLowerCase();
		BoiteEtatType filterType = parseTypeEtatFilter(typeEtat);
		Specification<Boite> spec = historiqueSpec(null, qTrim, filterType);

		long totalBoites = boiteRepository.count(spec);
		Specification<Bordereau> bordereauSpec = historiqueBordereauSpec(qTrim, filterType);
		Page<Bordereau> bordereauPage = bordereauRepository.findAll(
			bordereauSpec,
			PageRequest.of(
				pageable.getPageNumber(),
				pageable.getPageSize(),
				Sort.by(
					Sort.Order.desc("dateTransfert"),
					Sort.Order.asc("numeroAffiche")
				)
			)
		);

		List<Long> bordereauIds = bordereauPage.getContent().stream().map(Bordereau::getId).toList();
		if (bordereauIds.isEmpty()) {
			return new HistoriqueGroupedPageResponse(
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

		Map<Long, BordereauHistoriqueGroupDto> groupsById = new LinkedHashMap<>();
		for (Bordereau br : bordereauPage.getContent()) {
			groupsById.put(br.getId(), null);
		}
		for (BordereauHistoriqueGroupDto group : buildGroups(boxes)) {
			groupsById.put(group.bordereauId(), group);
		}
		List<BordereauHistoriqueGroupDto> pageContent = bordereauIds.stream()
			.map(groupsById::get)
			.filter(g -> g != null)
			.toList();

		return new HistoriqueGroupedPageResponse(
			pageContent,
			bordereauPage.getTotalElements(),
			totalBoites,
			bordereauPage.getTotalPages(),
			bordereauPage.getNumber(),
			bordereauPage.getSize()
		);
	}

	private Specification<Bordereau> historiqueBordereauSpec(String q, BoiteEtatType filterType) {
		return (root, query, cb) -> {
			Subquery<Long> sub = query.subquery(Long.class);
			Root<Boite> boxRoot = sub.from(Boite.class);
			Join<Boite, BoiteEtat> etatCourant = boxRoot.join("etatCourant", JoinType.INNER);
			Join<Boite, Bordereau> brJoin = boxRoot.join("bordereau", JoinType.INNER);
			Join<Boite, DocumentType> dt = boxRoot.join("documentType", JoinType.INNER);

			List<Predicate> boxPreds = new ArrayList<>();
			if (filterType != null) {
				boxPreds.add(cb.equal(etatCourant.get("typeEtat"), filterType));
			} else {
				boxPreds.add(etatCourant.get("typeEtat").in(BoiteEtatType.TRANSFERT, BoiteEtatType.DESTRUCTION));
			}
			if (q != null && !q.isEmpty()) {
				String like = "%" + q + "%";
				boxPreds.add(cb.or(
					cb.like(cb.lower(boxRoot.get("titre")), like),
					cb.like(cb.lower(brJoin.get("numeroAffiche")), like),
					cb.like(cb.lower(dt.get("title")), like),
					cb.like(cb.lower(etatCourant.get("utilisateur")), like),
					cb.like(cb.lower(etatCourant.get("positionId")), like),
					cb.like(cb.lower(etatCourant.get("commentaire")), like)
				));
			}
			sub.select(brJoin.get("id"));
			sub.where(cb.and(boxPreds.toArray(Predicate[]::new)));
			return root.get("id").in(sub);
		};
	}

	private List<BordereauHistoriqueGroupDto> buildGroups(List<Boite> all) {
		Map<Long, List<Boite>> byBordereau = new LinkedHashMap<>();
		for (Boite box : all) {
			Long brId = box.getBordereau().getId();
			byBordereau.computeIfAbsent(brId, k -> new ArrayList<>()).add(box);
		}

		return byBordereau.values().stream()
			.map(this::toGroup)
			.sorted(Comparator
				.comparing((BordereauHistoriqueGroupDto g) -> g.dateTransfert())
				.reversed()
				.thenComparing(BordereauHistoriqueGroupDto::numeroBordereau))
			.toList();
	}

	private BordereauHistoriqueGroupDto toGroup(List<Boite> boxes) {
		Bordereau br = boxes.get(0).getBordereau();
		Direction dir = br.getDirection();
		List<BoiteHistoriqueItemDto> items = boxes.stream()
			.sorted(Comparator
				.comparing(
					(Boite b) -> b.getEtatCourant() == null ? LocalDate.MIN : b.getEtatCourant().getDateEtat(),
					Comparator.reverseOrder()
				)
				.thenComparing(Boite::getTitre, String.CASE_INSENSITIVE_ORDER))
			.map(this::toItem)
			.toList();
		return new BordereauHistoriqueGroupDto(
			br.getId(),
			br.getNumeroAffiche(),
			br.getDateTransfert().toString(),
			dir == null ? null : dir.getLabel(),
			br.getAgent().getUserName(),
			items.size(),
			items
		);
	}

	private BoiteHistoriqueItemDto toItem(Boite box) {
		BoiteEtat etat = box.getEtatCourant();
		DocumentType dt = box.getDocumentType();
		ConservationRule rule = box.getConservationRule();
		return new BoiteHistoriqueItemDto(
			box.getId(),
			box.getTitre(),
			dt == null ? null : dt.getTitle(),
			etat == null ? null : etat.getTypeEtat().name(),
			etat == null ? null : etat.getDateEtat().toString(),
			etat == null ? null : etat.getUtilisateur(),
			etat == null ? null : EmplacementPlageFormatter.formatPlage(etat.getPositionId()),
			rule == null ? null : rule.getReference(),
			rule == null ? null : rule.getFinalDecision().name()
		);
	}

	private Specification<Boite> historiqueSpec(String userDirectionId, String q, BoiteEtatType filterType) {
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

			if (filterType != null) {
				preds.add(cb.equal(etatCourant.get("typeEtat"), filterType));
			} else {
				preds.add(etatCourant.get("typeEtat").in(BoiteEtatType.TRANSFERT, BoiteEtatType.DESTRUCTION));
			}

			if (userDirectionId != null && !userDirectionId.isBlank()) {
				Join<DocumentType, Direction> dir = dt.join("direction", JoinType.LEFT);
				preds.add(cb.equal(dir.get("id"), userDirectionId));
			}

			if (q != null && !q.isEmpty()) {
				String like = "%" + q + "%";
				preds.add(cb.or(
					cb.like(cb.lower(root.get("titre")), like),
					cb.like(cb.lower(br.get("numeroAffiche")), like),
					cb.like(cb.lower(dt.get("title")), like),
					cb.like(cb.lower(etatCourant.get("utilisateur")), like),
					cb.like(cb.lower(etatCourant.get("positionId")), like),
					cb.like(cb.lower(etatCourant.get("commentaire")), like)
				));
			}

			return cb.and(preds.toArray(Predicate[]::new));
		};
	}

	private static BoiteEtatType parseTypeEtatFilter(String typeEtat) {
		if (typeEtat == null || typeEtat.isBlank()) {
			return null;
		}
		try {
			BoiteEtatType parsed = BoiteEtatType.valueOf(typeEtat.trim().toUpperCase());
			if (parsed != BoiteEtatType.TRANSFERT && parsed != BoiteEtatType.DESTRUCTION) {
				throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_TYPE_ETAT",
					"Filtre typeEtat : TRANSFERT ou DESTRUCTION uniquement."
				);
			}
			return parsed;
		} catch (IllegalArgumentException ex) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"INVALID_TYPE_ETAT",
				"Filtre typeEtat : TRANSFERT ou DESTRUCTION uniquement."
			);
		}
	}
}
