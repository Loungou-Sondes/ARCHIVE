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

import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import ommp.archives.dto.documenttype.CreateDocumentTypeRequest;
import ommp.archives.dto.documenttype.DirectionOptionDto;
import ommp.archives.dto.documenttype.DocumentTypeResponse;
import ommp.archives.dto.documenttype.UpdateDocumentTypeRequest;
import ommp.archives.entity.Direction;
import ommp.archives.entity.DocumentType;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.DirectionRepository;
import ommp.archives.repository.DocumentTypeRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class DocumentTypeService {

	private final DocumentTypeRepository documentTypeRepository;
	private final DirectionRepository directionRepository;
	private final AuthorizationService authorization;

	public DocumentTypeService(
		DocumentTypeRepository documentTypeRepository,
		DirectionRepository directionRepository,
		AuthorizationService authorization
	) {
		this.documentTypeRepository = documentTypeRepository;
		this.directionRepository = directionRepository;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public List<DirectionOptionDto> listDirectionOptions(Authentication authentication) {
		authorization.requireAuthenticated(authentication);
		return directionRepository.findAllByOrderByIdAsc().stream()
			.map(d -> new DirectionOptionDto(d.getId(), d.getLabel()))
			.toList();
	}

	@Transactional(readOnly = true)
	public Page<DocumentTypeResponse> list(
		Authentication authentication,
		String q,
		String directionId,
		Pageable pageable
	) {
		authorization.requireAdmin(authentication);
		String qTrim = q == null ? null : q.trim();
		String dirTrim = directionId == null ? null : directionId.trim();
		Specification<DocumentType> spec = filterSpec(
			qTrim == null || qTrim.isEmpty() ? null : qTrim,
			dirTrim == null || dirTrim.isEmpty() ? null : dirTrim
		);
		return documentTypeRepository.findAll(spec, pageable).map(this::toResponse);
	}

	@Transactional(readOnly = true)
	public DocumentTypeResponse getById(Authentication authentication, Long id) {
		authorization.requireAdmin(authentication);
		DocumentType entity = documentTypeRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_TYPE_NOT_FOUND", "Type de document introuvable."));
		return toResponse(entity);
	}

	@Transactional
	public DocumentTypeResponse create(Authentication authentication, CreateDocumentTypeRequest request) {
		authorization.requireAdmin(authentication);
		String title = request.title().trim();
		String dirId = request.directionId() == null ? "" : request.directionId().trim();
		if (title.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TITLE", "Titre obligatoire.");
		}
		if (documentTypeRepository.existsByTitleIgnoreCase(title)) {
			throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_TITLE", "Un type de document avec ce titre existe déjà.");
		}
		Direction direction = null;
		if (!dirId.isEmpty()) {
			direction = directionRepository.findById(dirId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_DIRECTION", "Direction inconnue ou non référencée."));
		}
		DocumentType d = new DocumentType();
		d.setTitle(title);
		d.setDirection(direction);
		return toResponse(documentTypeRepository.save(d));
	}

	@Transactional
	public DocumentTypeResponse update(Authentication authentication, Long id, UpdateDocumentTypeRequest request) {
		authorization.requireAdmin(authentication);
		DocumentType d = documentTypeRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_TYPE_NOT_FOUND", "Type de document introuvable."));
		String title = request.title().trim();
		String dirId = request.directionId() == null ? "" : request.directionId().trim();
		if (title.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TITLE", "Titre obligatoire.");
		}
		if (documentTypeRepository.existsByTitleIgnoreCaseAndIdNot(title, id)) {
			throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_TITLE", "Un type de document avec ce titre existe déjà.");
		}
		Direction direction = null;
		if (!dirId.isEmpty()) {
			direction = directionRepository.findById(dirId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_DIRECTION", "Direction inconnue ou non référencée."));
		}
		d.setTitle(title);
		d.setDirection(direction);
		return toResponse(documentTypeRepository.save(d));
	}

	private Specification<DocumentType> filterSpec(String q, String directionId) {
		return (root, query, cb) -> {
			List<Predicate> preds = new ArrayList<>();
			var dirJoin = root.join("direction", JoinType.LEFT);
			if (q != null && !q.isEmpty()) {
				String like = "%" + q.toLowerCase() + "%";
				// Nom du type + direction (id ou libellé), pas d’autres champs
				preds.add(cb.or(
					cb.like(cb.lower(root.get("title")), like),
					cb.and(
						cb.isNotNull(dirJoin.get("id")),
						cb.or(
							cb.like(cb.lower(dirJoin.get("id")), like),
							cb.like(cb.lower(dirJoin.get("label")), like)
						)
					)
				));
			}
			if (directionId != null && !directionId.isEmpty()) {
				preds.add(cb.equal(dirJoin.get("id"), directionId));
			}
			return preds.isEmpty() ? cb.conjunction() : cb.and(preds.toArray(Predicate[]::new));
		};
	}

	private DocumentTypeResponse toResponse(DocumentType d) {
		Direction dir = d.getDirection();
		if (dir == null) {
			return new DocumentTypeResponse(d.getId(), d.getTitle(), null, null);
		}
		return new DocumentTypeResponse(d.getId(), d.getTitle(), dir.getId(), dir.getLabel());
	}

}
