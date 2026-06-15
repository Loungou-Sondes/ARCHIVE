package ommp.archives.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import ommp.archives.dto.boite.SemanticSearchHitDto;
import ommp.archives.dto.boite.SemanticSearchRequest;
import ommp.archives.dto.boite.SemanticSearchResponse;
import ommp.archives.entity.Boite;
import ommp.archives.entity.Bordereau;
import ommp.archives.entity.Direction;
import ommp.archives.entity.DocumentType;
import ommp.archives.entity.Emplacement;
import ommp.archives.entity.TypeEmp;
import ommp.archives.exception.ApiException;
import ommp.archives.ml.TextSimilarityEngine;
import ommp.archives.ml.TextSimilarityEngine.ScoredDocument;
import ommp.archives.ml.TextSimilarityEngine.SearchDocument;
import ommp.archives.repository.BoiteRepository;
import ommp.archives.repository.EmplacementRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class BoiteSemanticSearchService {

	private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
	private static final double MIN_SCORE = 0.15;

	private final BoiteRepository boiteRepository;
	private final EmplacementRepository emplacementRepository;
	private final EmplacementOccupationService emplacementOccupationService;
	private final AuthorizationService authorization;

	public BoiteSemanticSearchService(
		BoiteRepository boiteRepository,
		EmplacementRepository emplacementRepository,
		EmplacementOccupationService emplacementOccupationService,
		AuthorizationService authorization
	) {
		this.boiteRepository = boiteRepository;
		this.emplacementRepository = emplacementRepository;
		this.emplacementOccupationService = emplacementOccupationService;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public SemanticSearchResponse search(Authentication authentication, SemanticSearchRequest request) {
		authorization.requireAuthenticated(authentication);
		String query = trimToNull(request.query());
		if (query == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "SEMANTIC_QUERY_REQUIRED", "Saisissez une description de recherche.");
		}

		boolean admin = authorization.isAdmin(authentication);
		String userAccountId = admin ? null : authorization.resolveUserAccountId(authentication.getName());
		String userDirectionId = admin ? null : authorization.resolveUserDirectionId(authentication.getName());

		List<Boite> visible = boiteRepository.findAll(
			(root, cq, cb) -> {
				if (Boite.class.equals(cq.getResultType())) {
					root.fetch("bordereau", JoinType.INNER).fetch("direction", JoinType.LEFT);
					root.fetch("documentType", JoinType.INNER);
					root.fetch("etatCourant", JoinType.LEFT);
				}
				return visibilityPredicate(root, cb, admin, userAccountId, userDirectionId);
			}
		);

		if (visible.isEmpty()) {
			return new SemanticSearchResponse(query, List.of(), List.of());
		}

		Integer queryYear = extractYear(query);
		List<SearchDocument> documents = new ArrayList<>();
		Map<String, Boite> boiteById = new HashMap<>();
		for (Boite box : visible) {
			DocumentType type = box.getDocumentType();
			Bordereau bordereau = box.getBordereau();
			Direction direction = bordereau != null ? bordereau.getDirection() : null;
			String text = TextSimilarityEngine.buildDocumentText(
				box.getTitre(),
				box.getContenu(),
				box.getMotsCles(),
				type != null ? type.getTitle() : null,
				direction != null ? direction.getLabel() : null,
				bordereau != null ? bordereau.getNumeroAffiche() : null,
				box.getAnneeMin(),
				box.getAnneeMax()
			);
			if (text.isBlank()) {
				continue;
			}
			String id = String.valueOf(box.getId());
			documents.add(new SearchDocument(id, text));
			boiteById.put(id, box);
		}

		int limit = request.limit() != null && request.limit() > 0 ? Math.min(request.limit(), 30) : 15;
		List<ScoredDocument> ranked = TextSimilarityEngine.rank(query, documents, 0).stream()
			.filter(hit -> hit.score() >= MIN_SCORE)
			.sorted(yearAwareComparator(queryYear, boiteById))
			.limit(limit)
			.toList();

		String requestedEpi = normalizeEpiNumero(request.epiNumero());
		List<String> highlightBlocIds = new ArrayList<>();
		List<SemanticSearchHitDto> hits = new ArrayList<>();

		for (ScoredDocument scored : ranked) {
			Boite box = boiteById.get(scored.id());
			if (box == null) {
				continue;
			}
			Placement placement = resolvePlacement(box);
			boolean onEpi = requestedEpi != null && requestedEpi.equals(placement.epiNumero());
			if (onEpi) {
				highlightBlocIds.addAll(placement.blocIds());
			}
			hits.add(new SemanticSearchHitDto(
				box.getId(),
				box.getTitre(),
				scored.score(),
				placement.epiNumero(),
				placement.emplacement(),
				placement.blocIds(),
				onEpi
			));
		}

		return new SemanticSearchResponse(query, hits, List.copyOf(new LinkedHashSet<>(highlightBlocIds)));
	}

	private Comparator<ScoredDocument> yearAwareComparator(Integer queryYear, Map<String, Boite> boiteById) {
		return Comparator
			.comparingDouble((ScoredDocument hit) -> {
				double score = hit.score();
				if (queryYear == null) {
					return score;
				}
				Boite box = boiteById.get(hit.id());
				if (box != null && box.getAnneeMin() <= queryYear && queryYear <= box.getAnneeMax()) {
					return score + 0.15;
				}
				return score;
			})
			.reversed();
	}

	private Placement resolvePlacement(Boite box) {
		List<Emplacement> blocs = emplacementRepository.findByBoiteIdAndTypeEmpOrderByNumeroAsc(box.getId(), TypeEmp.BLOC);
		if (blocs.isEmpty()) {
			String display = emplacementOccupationService.resolveDisplay(box, box.getEtatCourant());
			return new Placement(null, display, List.of());
		}
		String epiNumero = epiNumeroFromBloc(blocs.get(0).getNumero());
		List<String> blocIds = blocs.stream().map(Emplacement::getId).toList();
		String emplacement = emplacementOccupationService.resolveDisplay(box, box.getEtatCourant());
		return new Placement(epiNumero, emplacement, blocIds);
	}

	private static String epiNumeroFromBloc(String blocNumero) {
		if (blocNumero == null || blocNumero.length() < 4) {
			return null;
		}
		return blocNumero.substring(0, blocNumero.length() - 3);
	}

	private static Integer extractYear(String query) {
		Matcher matcher = YEAR_PATTERN.matcher(query);
		if (!matcher.find()) {
			return null;
		}
		return Integer.parseInt(matcher.group());
	}

	private static String normalizeEpiNumero(String epiNumero) {
		String trimmed = trimToNull(epiNumero);
		return trimmed == null ? null : trimmed.trim();
	}

	private static Predicate visibilityPredicate(
		jakarta.persistence.criteria.Root<Boite> root,
		jakarta.persistence.criteria.CriteriaBuilder cb,
		boolean admin,
		String userAccountId,
		String userDirectionId
	) {
		if (admin) {
			return cb.conjunction();
		}
		Join<Boite, Bordereau> brJoin = root.join("bordereau", JoinType.INNER);
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
			return cb.disjunction();
		}
		return cb.or(visibility.toArray(Predicate[]::new));
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}

	private record Placement(String epiNumero, String emplacement, List<String> blocIds) {
	}
}
