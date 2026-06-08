package ommp.archives.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import ommp.archives.dto.boite.BoiteConsultationDto;
import ommp.archives.dto.boite.BoiteRechercheItemDto;
import ommp.archives.entity.Boite;
import ommp.archives.entity.BoiteEtat;
import ommp.archives.entity.Bordereau;
import ommp.archives.entity.Direction;
import ommp.archives.entity.DocumentType;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.BoiteRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class BoiteRechercheService {

	private final BoiteRepository boiteRepository;
	private final EmplacementOccupationService emplacementOccupationService;
	private final AuthorizationService authorization;

	public BoiteRechercheService(
		BoiteRepository boiteRepository,
		EmplacementOccupationService emplacementOccupationService,
		AuthorizationService authorization
	) {
		this.boiteRepository = boiteRepository;
		this.emplacementOccupationService = emplacementOccupationService;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public Page<BoiteRechercheItemDto> search(
		Authentication authentication,
		String q,
		String nom,
		String motsCles,
		Integer anneeMin,
		Integer anneeMax,
		Pageable pageable
	) {
		authorization.requireAuthenticated(authentication);

		boolean admin = authorization.isAdmin(authentication);
		String username = authentication.getName();
		String userAccountId = admin ? null : authorization.resolveUserAccountId(username);
		String userDirId = admin ? null : authorization.resolveUserDirectionId(username);

		String qTrim = trimToNull(q);
		String nomTrim = qTrim != null ? null : trimToNull(nom);
		String motsClesTrim = qTrim != null ? null : trimToNull(motsCles);
		Specification<Boite> spec = rechercheSpec(
			admin, userAccountId, userDirId, qTrim, nomTrim, motsClesTrim, anneeMin, anneeMax
		);
		return boiteRepository.findAll(spec, pageable).map(this::toItem);
	}

	@Transactional(readOnly = true)
	public BoiteConsultationDto getById(Authentication authentication, Long id) {
		authorization.requireAuthenticated(authentication);
		Boite box = boiteRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Boîte introuvable."));
		authorization.assertCanReadBoite(authentication, box);
		return toConsultation(box);
	}

	private BoiteRechercheItemDto toItem(Boite box) {
		Bordereau br = box.getBordereau();
		DocumentType dt = box.getDocumentType();
		Direction dir = br != null ? br.getDirection() : null;
		BoiteEtat etat = box.getEtatCourant();
		String emplacement = resolveEmplacementDisplay(box, etat);
		String typeEtat = etat != null && etat.getTypeEtat() != null ? etat.getTypeEtat().name() : null;
		String typeEtatLabel = etat != null && etat.getTypeEtat() != null ? etat.getTypeEtat().displayLabel() : null;
		return new BoiteRechercheItemDto(
			box.getId(),
			box.getTitre(),
			box.getMotsCles(),
			box.getAnneeMin(),
			box.getAnneeMax(),
			dt != null ? dt.getTitle() : null,
			br != null ? br.getId() : null,
			br != null ? br.getNumeroAffiche() : null,
			dir != null ? dir.getLabel() : null,
			typeEtat,
			typeEtatLabel,
			emplacement
		);
	}

	private BoiteConsultationDto toConsultation(Boite box) {
		Bordereau br = box.getBordereau();
		DocumentType dt = box.getDocumentType();
		Direction dir = br != null ? br.getDirection() : null;
		BoiteEtat etat = box.getEtatCourant();
		String emplacement = resolveEmplacementDisplay(box, etat);
		String typeEtat = etat != null && etat.getTypeEtat() != null ? etat.getTypeEtat().name() : null;
		String typeEtatLabel = etat != null && etat.getTypeEtat() != null ? etat.getTypeEtat().displayLabel() : null;
		int nombreBoites = br != null ? br.getNombreBoites() : 0;
		return new BoiteConsultationDto(
			box.getId(),
			box.getTitre(),
			box.getMotsCles(),
			box.getContenu(),
			box.getAnneeMin(),
			box.getAnneeMax(),
			box.getMetrageCm(),
			dt != null ? dt.getTitle() : null,
			br != null ? br.getId() : null,
			br != null ? br.getNumeroAffiche() : null,
			dir != null ? dir.getLabel() : null,
			nombreBoites,
			typeEtat,
			typeEtatLabel,
			emplacement
		);
	}

	/**
	 * Specification de recherche.
	 * Admin : voit toutes les boîtes.
	 * Agent : voit uniquement les boîtes des bordereaux qu'il a créés,
	 *         de même direction, ou sans direction.
	 */
	private Specification<Boite> rechercheSpec(
		boolean admin,
		String userAccountId,
		String userDirectionId,
		String q,
		String nom,
		String motsCles,
		Integer anneeMin,
		Integer anneeMax
	) {
		return (root, query, cb) -> {
			if (Boite.class.equals(query.getResultType())) {
				var brFetch = root.fetch("bordereau", JoinType.INNER);
				brFetch.fetch("direction", JoinType.LEFT);
				brFetch.fetch("agent", JoinType.LEFT);
				root.fetch("documentType", JoinType.INNER);
				root.fetch("etatCourant", JoinType.LEFT);
			}

			List<Predicate> preds = new ArrayList<>();
			Join<Boite, Bordereau> brJoin = root.join("bordereau", JoinType.INNER);

			if (!admin) {
				List<Predicate> visibility = new ArrayList<>();
				if (userAccountId != null && !userAccountId.isBlank()) {
					visibility.add(cb.equal(brJoin.get("agent").get("id"), userAccountId));
				}
				if (userDirectionId != null && !userDirectionId.isBlank()) {
					Join<Bordereau, Direction> dirJoin = brJoin.join("direction", JoinType.LEFT);
					visibility.add(cb.equal(dirJoin.get("id"), userDirectionId));
				}
				visibility.add(cb.isNull(brJoin.get("direction")));

				if (visibility.isEmpty()) {
					preds.add(cb.disjunction());
				} else {
					preds.add(cb.or(visibility.toArray(Predicate[]::new)));
				}
			}

			if (q != null && !q.isEmpty()) {
				String like = "%" + q.toLowerCase() + "%";
				Join<Boite, DocumentType> dtJoin = root.join("documentType", JoinType.INNER);
				preds.add(cb.or(
					cb.like(cb.lower(root.get("titre")), like),
					cb.like(cb.lower(cb.coalesce(root.get("motsCles"), cb.literal(""))), like),
					cb.like(cb.lower(cb.coalesce(root.get("contenu"), cb.literal(""))), like),
					cb.like(cb.lower(brJoin.get("numeroAffiche")), like),
					cb.like(cb.lower(dtJoin.get("title")), like)
				));
			} else {
				if (nom != null && !nom.isEmpty()) {
					String like = "%" + nom.toLowerCase() + "%";
					preds.add(cb.like(cb.lower(root.get("titre")), like));
				}

				if (motsCles != null && !motsCles.isEmpty()) {
					String like = "%" + motsCles.toLowerCase() + "%";
					preds.add(cb.or(
						cb.like(cb.lower(cb.coalesce(root.get("motsCles"), cb.literal(""))), like),
						cb.like(cb.lower(cb.coalesce(root.get("contenu"), cb.literal(""))), like)
					));
				}
			}

			if (anneeMin != null) {
				preds.add(cb.ge(root.get("anneeMax"), anneeMin));
			}
			if (anneeMax != null) {
				preds.add(cb.le(root.get("anneeMin"), anneeMax));
			}

			return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(Predicate[]::new));
		};
	}

	private String resolveEmplacementDisplay(Boite box, BoiteEtat etat) {
		return emplacementOccupationService.resolveDisplay(box, etat);
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

}
