package ommp.archives.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import ommp.archives.dto.bordereau.BlocEmplacementSuggestionDto;
import ommp.archives.dto.bordereau.BoiteBlocsSuggestionDto;
import ommp.archives.dto.bordereau.BoiteResponse;
import ommp.archives.dto.bordereau.BordereauDetailResponse;
import ommp.archives.dto.bordereau.BordereauFormOptionsDto;
import ommp.archives.dto.bordereau.BordereauListItemResponse;
import ommp.archives.dto.bordereau.CreateBordereauBoiteRequest;
import ommp.archives.dto.bordereau.CreateBordereauRequest;
import ommp.archives.dto.bordereau.NextNumeroAfficheResponse;
import ommp.archives.dto.bordereau.RegleConservationValidePreviewDto;
import ommp.archives.dto.bordereau.ValiderBordereauAffectationRequest;
import ommp.archives.dto.bordereau.ValiderBoiteAffectationItem;
import ommp.archives.dto.documenttype.DirectionOptionDto;
import ommp.archives.dto.documenttype.DocumentTypeResponse;
import ommp.archives.entity.Boite;
import ommp.archives.entity.BoiteEtatAction;
import ommp.archives.entity.Bordereau;
import ommp.archives.entity.BordereauStatut;
import ommp.archives.entity.ConservationRule;
import ommp.archives.entity.ConservationRuleStatus;
import ommp.archives.entity.Direction;
import ommp.archives.entity.DocumentType;
import ommp.archives.entity.Emplacement;
import ommp.archives.entity.TypeEmp;
import ommp.archives.entity.UserAccount;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.BoiteRepository;
import ommp.archives.repository.BordereauRepository;
import ommp.archives.repository.ConservationRuleRepository;
import ommp.archives.repository.DirectionRepository;
import ommp.archives.repository.DocumentTypeRepository;
import ommp.archives.repository.EmplacementRepository;
import ommp.archives.repository.UserAccountRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class BordereauService {

	private static final Set<Integer> METRAGES_AUTORISES = Set.of(10, 20, 30, 40);

	private final BordereauRepository bordereauRepository;
	private final BoiteRepository boiteRepository;
	private final EmplacementRepository emplacementRepository;
	private final UserAccountRepository userAccountRepository;
	private final DirectionRepository directionRepository;
	private final DocumentTypeRepository documentTypeRepository;
	private final ConservationRuleRepository conservationRuleRepository;
	private final ConservationRuleResolver conservationRuleResolver;
	private final BoiteEtatService boiteEtatService;
	private final EmplacementOccupationService emplacementOccupationService;
	private final AuthorizationService authorization;

	public BordereauService(
		BordereauRepository bordereauRepository,
		BoiteRepository boiteRepository,
		EmplacementRepository emplacementRepository,
		UserAccountRepository userAccountRepository,
		DirectionRepository directionRepository,
		DocumentTypeRepository documentTypeRepository,
		ConservationRuleRepository conservationRuleRepository,
		ConservationRuleResolver conservationRuleResolver,
		BoiteEtatService boiteEtatService,
		EmplacementOccupationService emplacementOccupationService,
		AuthorizationService authorization
	) {
		this.bordereauRepository = bordereauRepository;
		this.boiteRepository = boiteRepository;
		this.emplacementRepository = emplacementRepository;
		this.userAccountRepository = userAccountRepository;
		this.directionRepository = directionRepository;
		this.documentTypeRepository = documentTypeRepository;
		this.conservationRuleRepository = conservationRuleRepository;
		this.conservationRuleResolver = conservationRuleResolver;
		this.boiteEtatService = boiteEtatService;
		this.emplacementOccupationService = emplacementOccupationService;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public BordereauFormOptionsDto formOptions(Authentication authentication, Integer anneeProchainNumero) {
		authorization.requireAuthenticated(authentication);
		List<DirectionOptionDto> dirs = directionRepository.findAllByOrderByIdAsc().stream()
			.map(d -> new DirectionOptionDto(d.getId(), d.getLabel()))
			.toList();
		List<DocumentTypeResponse> types = documentTypeRepository.findAllOrderedForOptions();
		int annee = anneeProchainNumero != null ? anneeProchainNumero : java.time.LocalDate.now().getYear();
		if (annee < 1900 || annee > 2100) {
			annee = java.time.LocalDate.now().getYear();
		}
		int nextRang = bordereauRepository.findMaxRangForAnneeAndStatut(annee, BordereauStatut.AFFECTE) + 1;
		String prochainNumeroAffiche = BordereauNumeroSupport.formatOfficialNumero(nextRang, annee);
		boolean admin = authorization.isAdmin(authentication);
		String userDirId = admin ? null : authorization.resolveUserDirectionId(authentication.getName());
		String userDirLabel = resolveDirectionLabel(userDirId);
		boolean directionLocked = !admin && userDirId != null && !userDirId.isBlank();
		return new BordereauFormOptionsDto(
			dirs,
			types,
			prochainNumeroAffiche,
			userDirId,
			userDirLabel,
			directionLocked
		);
	}

	@Transactional(readOnly = true)
	public NextNumeroAfficheResponse previewNextNumeroAffiche(Authentication authentication, int annee) {
		authorization.requireAuthenticated(authentication);
		if (annee < 1900 || annee > 2100) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ANNEE", "Année hors plage (1900–2100).");
		}
		int nextRang = bordereauRepository.findMaxRangForAnneeAndStatut(annee, BordereauStatut.AFFECTE) + 1;
		return new NextNumeroAfficheResponse(BordereauNumeroSupport.formatOfficialNumero(nextRang, annee));
	}

	@Transactional(readOnly = true)
	public RegleConservationValidePreviewDto previewRegleValideForDocumentType(Authentication authentication, Long documentTypeId) {
		authorization.requireAuthenticated(authentication);
		if (documentTypeId == null) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NO_DOCUMENT_TYPE", "Type de document obligatoire.");
		}
		if (!documentTypeRepository.existsById(documentTypeId)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_DOCUMENT_TYPE", "Type de document inconnu.");
		}
		ConservationRule rule = conservationRuleResolver
			.findValideRuleForDocumentType(documentTypeId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_RULE_FOR_TYPE", "Aucune règle de conservation valide pour ce type de document."));
		return new RegleConservationValidePreviewDto(
			rule.getReference(),
			rule.getFinalDecision().name(),
			rule.isActiveUnknown(),
			rule.getActiveYears(),
			rule.isSemiActiveUnknown(),
			rule.getSemiActiveYears()
		);
	}

	@Transactional(readOnly = true)
	public List<BoiteBlocsSuggestionDto> suggestEmplacementBlocs(Authentication authentication, List<Integer> metragesCm) {
		authorization.requireAuthenticated(authentication);
		authorization.requireAdmin(authentication);
		if (metragesCm == null || metragesCm.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NO_METRAGES", "Indiquez au moins un métrage de boîte.");
		}
		if (metragesCm.size() > 50) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_BOXES", "Maximum 50 boîtes par proposition.");
		}
		List<Emplacement> pool = new ArrayList<>(
			emplacementRepository.findByTypeEmpAndBoiteIdIsNullOrderByNumeroAsc(TypeEmp.BLOC));
		Set<String> reserved = new HashSet<>();
		List<BoiteBlocsSuggestionDto> result = new ArrayList<>();
		for (Integer metrage : metragesCm) {
			if (metrage == null || !METRAGES_AUTORISES.contains(metrage)) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_METRAGE", "Métrage autorisé : 10, 20, 30 ou 40 cm.");
			}
			int required = blocsRequiredForMetrage(metrage);
			boolean splitReco = false;
			boolean multiReco = false;
			Integer premierePlageBlocs = null;
			List<Emplacement> group = findConsecutiveFreeBlocs(pool, required, reserved);
			/** Sinon fragmentation : deux plages même tablette, puis multi-tablettes. */
			if (group.size() < required) {
				Optional<SplitBlocPick> splitSameTablette = findSplitTwoSegmentAssignment(pool, required, reserved);
				if (splitSameTablette.isPresent()) {
					group = splitSameTablette.get().combined();
					splitReco = true;
					premierePlageBlocs = splitSameTablette.get().premierePlageNombre();
				}
				else {
					Optional<SplitBlocPick> cross = findCrossTabletteAssignment(pool, required, reserved);
					if (cross.isPresent()) {
						group = cross.get().combined();
						multiReco = true;
						premierePlageBlocs = cross.get().premierePlageNombre();
					}
				}
			}
			for (Emplacement e : group) {
				reserved.add(e.getId());
			}
			List<BlocEmplacementSuggestionDto> blocs = group.stream()
				.map(e -> new BlocEmplacementSuggestionDto(e.getId(), e.getNumero()))
				.toList();
			result.add(new BoiteBlocsSuggestionDto(metrage, required, blocs, splitReco, premierePlageBlocs, multiReco));
		}
		return result;
	}

	/**
	 * Liste tous les blocs libres (non encore attribués à une boîte) triés par numéro.
	 * Sert au mode d'affectation manuel : l'utilisateur voit les emplacements libres et les choisit.
	 */
	@Transactional(readOnly = true)
	public Page<BlocEmplacementSuggestionDto> listBlocsLibres(
		Authentication authentication,
		String q,
		Pageable pageable
	) {
		authorization.requireAuthenticated(authentication);
		authorization.requireAdmin(authentication);
		String qTrim = trimToNull(q);
		return emplacementRepository.findFreeBlocsPage(qTrim, pageable)
			.map(e -> new BlocEmplacementSuggestionDto(e.getId(), e.getNumero()));
	}

	/** Nombre de blocs physiques selon le métrage linéaire de la boîte. */
	public static int blocsRequiredForMetrage(int metrageCm) {
		return switch (metrageCm) {
			case 10 -> 1;
			case 20 -> 2;
			case 30 -> 3;
			case 40 -> 4;
			default -> throw new IllegalArgumentException("Métrage non supporté : " + metrageCm);
		};
	}

	private DocumentTypeResponse toDocumentTypeMini(DocumentType dt) {
		Direction dir = dt.getDirection();
		if (dir == null) {
			return new DocumentTypeResponse(dt.getId(), dt.getTitle(), null, null);
		}
		return new DocumentTypeResponse(dt.getId(), dt.getTitle(), dir.getId(), dir.getLabel());
	}

	@Transactional(readOnly = true)
	public Page<BordereauListItemResponse> list(
		Authentication authentication,
		String q,
		String directionId,
		Integer anneeMin,
		Integer anneeMax,
		Pageable pageable
	) {
		authorization.requireAuthenticated(authentication);
		boolean admin = authorization.isAdmin(authentication);
		String username = authentication.getName();
		String userAccountId = admin ? null : authorization.resolveUserAccountId(username);
		String userDirId = admin ? null : authorization.resolveUserDirectionId(username);
		Specification<Bordereau> spec = listSpec(
			admin,
			userAccountId,
			userDirId,
			trimToNull(q),
			BordereauStatut.AFFECTE,
			CreatorFilter.TOUS,
			trimToNull(directionId),
			anneeMin,
			anneeMax
		);
		return bordereauRepository.findAll(spec, pageable).map(b -> toListItem(b, false, null));
	}

	@Transactional(readOnly = true)
	public long countEnAttente(Authentication authentication) {
		authorization.requireAuthenticated(authentication);
		boolean admin = authorization.isAdmin(authentication);
		String username = authentication.getName();
		String userAccountId = admin ? null : authorization.resolveUserAccountId(username);
		String userDirId = admin ? null : authorization.resolveUserDirectionId(username);
		CreatorFilter creator = admin ? CreatorFilter.ADMIN_UNIQUEMENT : CreatorFilter.TOUS;
		return bordereauRepository.count(
			listSpec(admin, userAccountId, userDirId, null, BordereauStatut.EN_ATTENTE, creator)
		);
	}

	/** Bordereaux soumis par des agents, en attente de validation admin. */
	@Transactional(readOnly = true)
	public long countValidationAgents(Authentication authentication) {
		authorization.requireAdmin(authentication);
		return bordereauRepository.count(
			listSpec(true, null, null, null, BordereauStatut.EN_ATTENTE, CreatorFilter.AGENT_UNIQUEMENT)
		);
	}

	@Transactional(readOnly = true)
	public Page<BordereauListItemResponse> listEnAttente(
		Authentication authentication,
		String q,
		String directionId,
		Integer anneeMin,
		Integer anneeMax,
		Pageable pageable
	) {
		authorization.requireAuthenticated(authentication);
		boolean admin = authorization.isAdmin(authentication);
		String username = authentication.getName();
		String userAccountId = admin ? null : authorization.resolveUserAccountId(username);
		String userDirId = admin ? null : authorization.resolveUserDirectionId(username);
		CreatorFilter creator = admin ? CreatorFilter.ADMIN_UNIQUEMENT : CreatorFilter.TOUS;
		return mapEnAttentePage(
			bordereauRepository.findAll(
				listSpec(
					admin,
					userAccountId,
					userDirId,
					trimToNull(q),
					BordereauStatut.EN_ATTENTE,
					creator,
					trimToNull(directionId),
					anneeMin,
					anneeMax
				),
				pageable
			)
		);
	}

	@Transactional(readOnly = true)
	public Page<BordereauListItemResponse> listValidationAgents(
		Authentication authentication,
		String q,
		String directionId,
		Integer anneeMin,
		Integer anneeMax,
		Pageable pageable
	) {
		authorization.requireAdmin(authentication);
		return mapEnAttentePage(
			bordereauRepository.findAll(
				listSpec(
					true,
					null,
					null,
					trimToNull(q),
					BordereauStatut.EN_ATTENTE,
					CreatorFilter.AGENT_UNIQUEMENT,
					trimToNull(directionId),
					anneeMin,
					anneeMax
				),
				pageable
			)
		);
	}

	private Page<BordereauListItemResponse> mapEnAttentePage(Page<Bordereau> page) {
		List<Long> bordereauIds = page.getContent().stream().map(Bordereau::getId).toList();
		Map<Long, String> regleAlertByBordereauId = summarizeRegleActiveUnknownByBordereauIds(bordereauIds);
		return page.map(b -> {
			String resume = regleAlertByBordereauId.get(b.getId());
			return toListItem(b, resume != null, resume);
		});
	}

	@Transactional
	public BordereauDetailResponse getById(Authentication authentication, Long id) {
		authorization.requireAuthenticated(authentication);
		Bordereau b = bordereauRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BORDEREAU_NOT_FOUND", "Bordereau introuvable."));
		authorization.assertCanReadBordereau(authentication, b);
		if (b.getStatut() == BordereauStatut.EN_ATTENTE) {
			ensureEnAttenteBordereauSansEmplacement(b);
		}
		return toDetail(b);
	}

	@Transactional
	public BordereauDetailResponse create(Authentication authentication, CreateBordereauRequest request) {
		authorization.requireAuthenticated(authentication);
		UserAccount agent = userAccountRepository.findByUserName(authentication.getName())
			.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Utilisateur introuvable."));

		if (request.boites() == null || request.boites().isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NO_BOXES", "Au moins une boîte est obligatoire.");
		}

		Direction direction = resolveDirectionForWrite(authentication, request.directionId());

		int annee = request.dateTransfert().getYear();
		boolean enAttente = Boolean.TRUE.equals(request.mettreEnAttente());
		if (!authorization.isAdmin(authentication)) {
			if (!enAttente || hasEmplacementAssignmentsInRequest(request)) {
				throw new ApiException(
					HttpStatus.FORBIDDEN,
					"AGENT_AFFECTATION_FORBIDDEN",
					"Enregistrez le bordereau en attente : l’affectation des emplacements est réservée à l’administrateur."
				);
			}
			enAttente = true;
		}

		Bordereau b = new Bordereau();
		if (enAttente) {
			b.setRangNumero(0);
			b.setAnneeNumero(annee);
			b.setNumeroAffiche(BordereauNumeroSupport.temporaryPlaceholder());
		} else {
			int nextRang = bordereauRepository.findMaxRangForAnneeAndStatut(annee, BordereauStatut.AFFECTE) + 1;
			BordereauNumeroSupport.assignOfficialNumero(b, nextRang, annee);
		}
		b.setDirection(direction);
		b.setDateTransfert(request.dateTransfert());
		b.setObservation(trimToNull(request.observation()));
		b.setStatut(enAttente ? BordereauStatut.EN_ATTENTE : BordereauStatut.AFFECTE);
		b.setAgent(agent);

		if (!enAttente) {
			validateBoiteEmplacementAssignments(request.boites());
		}

		String utilisateur = authentication.getName();
		List<List<String>> blocIdsPerBoite = new ArrayList<>();
		for (CreateBordereauBoiteRequest br : request.boites()) {
			ConservationRule ruleForBoite = resolveValideRuleForBoite(br.documentTypeId());
			ruleForBoite = completeActiveDurationForBoiteLink(ruleForBoite, br.renseignerAnneesActives(), br.titre().trim());
			DocumentType dt = documentTypeRepository.findById(br.documentTypeId())
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_DOCUMENT_TYPE", "Type de document inconnu."));
			Boite box = new Boite();
			box.setBordereau(b);
			box.setTitre(br.titre().trim());
			box.setAnneeMin(br.anneeMin());
			box.setAnneeMax(br.anneeMax());
			box.setMetrageCm(br.metrageCm());
			box.setContenu(trimToNull(br.contenu()));
			box.setMotsCles(br.motsCles().trim());
			box.setDocumentType(dt);
			box.setConservationRule(ruleForBoite);
			if (!enAttente) {
				List<String> blocIds = resolveBlocIds(br);
				if (!blocIds.isEmpty()) {
					List<Emplacement> sortedBlocs = blocIds.stream()
						.map((String id) -> emplacementRepository.findById(id)
							.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "BLOC_NOT_FOUND", "Emplacement de bloc introuvable.")))
						.sorted(Comparator.comparing(Emplacement::getNumero))
						.toList();
					blocIdsPerBoite.add(sortedBlocs.stream().map(Emplacement::getId).toList());
				} else {
					blocIdsPerBoite.add(List.of());
				}
			} else {
				blocIdsPerBoite.add(List.of());
			}
			b.getBoites().add(box);
		}
		b.setNombreBoites(request.boites().size());

		Bordereau saved = bordereauRepository.saveAndFlush(b);
		if (enAttente) {
			BordereauNumeroSupport.applyProvisionalNumero(saved);
			saved = bordereauRepository.saveAndFlush(saved);
			ensureEnAttenteBordereauSansEmplacement(saved);
		} else {
			syncEmplacementBoiteIds(
				boiteRepository.findWithDocumentTypeByBordereauId(saved.getId()),
				blocIdsPerBoite
			);
		}
		for (Boite box : saved.getBoites()) {
			boiteEtatService.recordState(
				box,
				BoiteEtatAction.CREATION,
				utilisateur,
				"Création de la boîte"
			);
			if (!enAttente) {
				boiteEtatService.recordState(
					box,
					BoiteEtatAction.ASSIGNATION,
					utilisateur,
					"Affectation validée à la création"
				);
			}
		}
		return toDetail(saved);
	}

	@Transactional
	public BordereauDetailResponse updateEnAttente(Authentication authentication, Long id, CreateBordereauRequest request) {
		authorization.requireAuthenticated(authentication);
		Bordereau b = bordereauRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BORDEREAU_NOT_FOUND", "Bordereau introuvable."));
		authorization.assertCanReadBordereau(authentication, b);
		if (b.getStatut() != BordereauStatut.EN_ATTENTE) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"BORDEREAU_NOT_EN_ATTENTE",
				"Seuls les bordereaux en attente peuvent être modifiés."
			);
		}
		if (request.boites() == null || request.boites().isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NO_BOXES", "Au moins une boîte est obligatoire.");
		}
		if (hasEmplacementAssignmentsInRequest(request)) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"EMPLACEMENT_VIA_VALIDATION",
				"L’affectation des emplacements se fait uniquement via « Valider l’affectation » (administrateur)."
			);
		}

		Direction direction = resolveDirectionForWrite(authentication, request.directionId());

		List<Boite> existingBoites = boiteRepository.findByBordereauId(b.getId());
		releaseEmplacementsForBoites(existingBoites);
		boiteRepository.deleteAll(existingBoites);
		b.getBoites().clear();
		b.setDirection(direction);
		b.setDateTransfert(request.dateTransfert());
		b.setAnneeNumero(request.dateTransfert().getYear());
		b.setObservation(trimToNull(request.observation()));

		for (CreateBordereauBoiteRequest br : request.boites()) {
			ConservationRule ruleForBoite = resolveValideRuleForBoite(br.documentTypeId());
			ruleForBoite = completeActiveDurationForBoiteLink(ruleForBoite, br.renseignerAnneesActives(), br.titre().trim());
			DocumentType dt = documentTypeRepository.findById(br.documentTypeId())
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_DOCUMENT_TYPE", "Type de document inconnu."));
			Boite box = new Boite();
			box.setBordereau(b);
			box.setTitre(br.titre().trim());
			box.setAnneeMin(br.anneeMin());
			box.setAnneeMax(br.anneeMax());
			box.setMetrageCm(br.metrageCm());
			box.setContenu(trimToNull(br.contenu()));
			box.setMotsCles(br.motsCles().trim());
			box.setDocumentType(dt);
			box.setConservationRule(ruleForBoite);
			b.getBoites().add(box);
		}
		b.setNombreBoites(request.boites().size());

		Bordereau saved = bordereauRepository.saveAndFlush(b);
		ensureEnAttenteBordereauSansEmplacement(saved);
		for (Boite box : saved.getBoites()) {
			boiteEtatService.recordState(
				box,
				BoiteEtatAction.CREATION,
				authentication.getName(),
				"Bordereau en attente (sans affectation d'emplacement)"
			);
		}
		return toDetail(saved);
	}

	@Transactional
	public void deleteEnAttente(Authentication authentication, Long id) {
		authorization.requireAuthenticated(authentication);
		Bordereau b = bordereauRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BORDEREAU_NOT_FOUND", "Bordereau introuvable."));
		authorization.assertCanReadBordereau(authentication, b);
		if (b.getStatut() != BordereauStatut.EN_ATTENTE) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"BORDEREAU_NOT_EN_ATTENTE",
				"Seuls les bordereaux en attente peuvent être supprimés depuis les alertes."
			);
		}
		List<Boite> boites = boiteRepository.findByBordereauId(b.getId());
		releaseEmplacementsForBoites(boites);
		boiteRepository.deleteAll(boites);
		bordereauRepository.delete(b);
	}

	@Transactional
	public BordereauDetailResponse validerAffectation(
		Authentication authentication,
		Long id,
		ValiderBordereauAffectationRequest request
	) {
		authorization.requireAuthenticated(authentication);
		authorization.requireAdmin(authentication);
		Bordereau b = bordereauRepository.findById(id)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BORDEREAU_NOT_FOUND", "Bordereau introuvable."));
		authorization.assertCanReadBordereau(authentication, b);
		if (b.getStatut() != BordereauStatut.EN_ATTENTE) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"BORDEREAU_NOT_EN_ATTENTE",
				"Seuls les bordereaux en attente peuvent être validés."
			);
		}

		List<Boite> boites = boiteRepository.findWithDocumentTypeByBordereauId(b.getId());
		if (boites.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "NO_BOXES", "Le bordereau ne contient aucune boîte.");
		}

		Map<Long, ValiderBoiteAffectationItem> itemByBoiteId = new LinkedHashMap<>();
		if (request != null && request.boites() != null) {
			for (ValiderBoiteAffectationItem item : request.boites()) {
				if (item.boiteId() != null) {
					itemByBoiteId.put(item.boiteId(), item);
				}
			}
		}
		for (Boite box : boites) {
			ConservationRule rule = resolveRuleForBoite(box);
			if (rule != null) {
				ValiderBoiteAffectationItem item = itemByBoiteId.get(box.getId());
				Integer activeYears = item == null ? null : item.renseignerAnneesActives();
				ConservationRule updated = completeActiveDurationForBoiteLink(rule, activeYears, box.getTitre());
				box.setConservationRule(updated);
			}
		}
		boiteRepository.saveAll(boites);
		assertBordereauRulesCompleteForAffectation(b);
		ensureEnAttenteBordereauSansEmplacement(b);

		Map<Long, List<String>> overrideByBoiteId = new LinkedHashMap<>();
		if (request != null && request.boites() != null) {
			for (ValiderBoiteAffectationItem item : request.boites()) {
				if (item.boiteId() == null) {
					continue;
				}
				List<String> ids = item.emplacementBlocIds() == null
					? List.of()
					: item.emplacementBlocIds().stream().map(this::trimToNull).filter(Objects::nonNull).toList();
				overrideByBoiteId.put(item.boiteId(), ids);
			}
		}

		Set<Long> boiteIdsAttendus = boites.stream().map(Boite::getId).collect(Collectors.toSet());
		for (Long boiteId : overrideByBoiteId.keySet()) {
			if (!boiteIdsAttendus.contains(boiteId)) {
				throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_BOITE_ID",
					"Une boîte indiquée n’appartient pas à ce bordereau."
				);
			}
		}

		List<List<String>> blocIdsPerBoite = new ArrayList<>();
		for (Boite box : boites) {
			if (!overrideByBoiteId.containsKey(box.getId())) {
				throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"MISSING_BOITE_AFFECTATION",
					"Indiquez les emplacements pour la boîte « " + box.getTitre() + " »."
				);
			}
			List<String> blocIds = overrideByBoiteId.get(box.getId());
			if (blocIds == null || blocIds.isEmpty()) {
				throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"NO_EMPLACEMENT",
					"La boîte « " + box.getTitre() + " » doit avoir au moins un bloc d’emplacement."
				);
			}
			assertBlocsAvailableForValidation(box, blocIds);
			List<Emplacement> sortedBlocs = blocIds.stream()
				.map((String blocId) -> emplacementRepository.findById(blocId)
					.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "BLOC_NOT_FOUND", "Emplacement de bloc introuvable.")))
				.sorted(Comparator.comparing(Emplacement::getNumero))
				.toList();
			blocIdsPerBoite.add(sortedBlocs.stream().map(Emplacement::getId).toList());
		}

		int annee = b.getDateTransfert().getYear();
		int nextRang = bordereauRepository.findMaxRangForAnneeAndStatut(annee, BordereauStatut.AFFECTE) + 1;
		BordereauNumeroSupport.assignOfficialNumero(b, nextRang, annee);
		b.setStatut(BordereauStatut.AFFECTE);
		b.setNotifAgentVue(false);
		boiteRepository.saveAll(boites);
		Bordereau saved = bordereauRepository.saveAndFlush(b);
		syncEmplacementBoiteIds(boites, blocIdsPerBoite);
		for (Boite box : saved.getBoites()) {
			boiteEtatService.recordState(
				box,
				BoiteEtatAction.ASSIGNATION,
				authentication.getName(),
				"Affectation validée"
			);
		}
		return toDetail(saved);
	}

	private void releaseEmplacementsForBoites(List<Boite> boites) {
		for (Boite box : boites) {
			boiteEtatService.libererEmplacementsPhysiques(box);
			boiteEtatService.clearEtatsForBoite(box);
		}
		if (!boites.isEmpty()) {
			boiteRepository.saveAll(boites);
		}
	}

	private void assertBlocsAvailableForValidation(Boite box, List<String> blocIds) {
		int required = blocsRequiredForMetrage(box.getMetrageCm());
		if (blocIds.size() != required) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"INVALID_BLOC_COUNT",
				"Pour une boîte de " + box.getMetrageCm() + " cm, il faut exactement " + required + " bloc(s)."
			);
		}
		Long boxId = box.getId();
		for (String blocId : blocIds) {
			Emplacement emp = emplacementRepository.findById(blocId)
				.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "BLOC_NOT_FOUND", "Emplacement de bloc inconnu."));
			if (emp.getTypeEmp() != TypeEmp.BLOC) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMPLACEMENT_TYPE", "Chaque emplacement proposé doit être un bloc.");
			}
			if (emplacementOccupationService.isBlocOccupiedByOtherBoite(blocId, boxId)) {
				throw new ApiException(
					HttpStatus.CONFLICT,
					"BLOC_OCCUPE",
					"Le bloc « " + emp.getNumero() + " » est déjà occupé par une autre boîte."
				);
			}
		}
	}

	/** Met à jour {@code EMPLACEMENTS.ID_BOITE} pour tous les blocs occupés par chaque boîte. */
	private void syncEmplacementBoiteIds(List<Boite> boitesInOrder, List<List<String>> blocIdsPerBoite) {
		List<Emplacement> toUpdate = new ArrayList<>();
		for (int i = 0; i < boitesInOrder.size() && i < blocIdsPerBoite.size(); i++) {
			Boite box = boitesInOrder.get(i);
			for (String blocId : blocIdsPerBoite.get(i)) {
				Emplacement emp = emplacementRepository.findById(blocId)
					.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY", "Bloc introuvable après enregistrement."));
				emp.setBoiteId(box.getId());
				toUpdate.add(emp);
			}
		}
		if (!toUpdate.isEmpty()) {
			emplacementRepository.saveAll(toUpdate);
		}
	}

	private ConservationRule resolveValideRuleForBoite(Long documentTypeId) {
		return conservationRuleResolver
			.findValideRuleForDocumentType(documentTypeId)
			.orElseThrow(() -> new ApiException(
				HttpStatus.BAD_REQUEST,
				"NO_RULE_FOR_TYPE",
				"Chaque boîte doit référencer un type de document ayant au moins une règle de conservation valide."
			));
	}

	private ConservationRule completeActiveDurationForBoiteLink(
		ConservationRule rule,
		Integer renseignerAnneesActives,
		String boiteLabel
	) {
		if (!rule.isActiveUnknown()) {
			return rule;
		}
		if (renseignerAnneesActives == null || renseignerAnneesActives < 0) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"RULE_ACTIVE_UNKNOWN",
				"La boîte « " + boiteLabel + " » : la règle « " + rule.getReference()
					+ " » a une durée active inconnue. Renseignez le nombre d'années actives pour lier cette boîte."
			);
		}
		rule.setActiveUnknown(false);
		rule.setActiveYears(renseignerAnneesActives);
		return conservationRuleRepository.save(rule);
	}

	private void assertRuleCompleteForAffectation(ConservationRule rule, String boiteLabel) {
		if (rule.isActiveUnknown()) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"RULE_ACTIVE_UNKNOWN",
				"La boîte « " + boiteLabel + " » : la règle « " + rule.getReference()
					+ " » a une durée active inconnue. Renseignez le nombre d'années actives pour lier cette boîte."
			);
		}
	}

	private void assertBordereauRulesCompleteForAffectation(Bordereau b) {
		for (Boite box : b.getBoites()) {
			ConservationRule rule = resolveRuleForBoite(box);
			if (rule != null) {
				assertRuleCompleteForAffectation(rule, box.getTitre());
			}
		}
	}

	private ConservationRule resolveRuleForBoite(Boite box) {
		ConservationRule linked = box.getConservationRule();
		if (linked != null && linked.getStatus() == ConservationRuleStatus.VALIDE) {
			return linked;
		}
		return conservationRuleResolver
			.findValideRuleForDocumentType(box.getDocumentType().getId())
			.orElse(null);
	}

	private Map<Long, String> summarizeRegleActiveUnknownByBordereauIds(List<Long> bordereauIds) {
		if (bordereauIds == null || bordereauIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, LinkedHashSet<String>> refsByBordereau = new LinkedHashMap<>();
		for (Boite box : boiteRepository.findWithRuleContextByBordereauIdIn(bordereauIds)) {
			ConservationRule rule = resolveRuleForBoite(box);
			if (rule != null && rule.isActiveUnknown()) {
				Long bordereauId = box.getBordereau().getId();
				refsByBordereau.computeIfAbsent(bordereauId, k -> new LinkedHashSet<>()).add(rule.getReference());
			}
		}
		Map<Long, String> out = new LinkedHashMap<>();
		for (Map.Entry<Long, LinkedHashSet<String>> e : refsByBordereau.entrySet()) {
			out.put(e.getKey(), String.join(", ", e.getValue()));
		}
		return out;
	}

	private RegleAlertSummary regleAlertSummaryForBordereau(Bordereau b) {
		Map<Long, String> map = summarizeRegleActiveUnknownByBordereauIds(List.of(b.getId()));
		String resume = map.get(b.getId());
		return new RegleAlertSummary(resume != null, resume);
	}

	private record RegleAlertSummary(boolean regleDureeActiveInconnue, String reglesEnAlerteResume) {
	}


	private void validateBoiteEmplacementAssignments(List<CreateBordereauBoiteRequest> boites) {
		Set<String> seen = new HashSet<>();
		for (CreateBordereauBoiteRequest br : boites) {
			List<String> blocIds = resolveBlocIds(br);
			if (blocIds.isEmpty()) {
				continue;
			}
			int required = blocsRequiredForMetrage(br.metrageCm());
			if (blocIds.size() != required) {
				throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"INVALID_BLOC_COUNT",
					"Pour une boîte de " + br.metrageCm() + " cm, il faut exactement " + required + " bloc(s) (prévu par le métrage)."
				);
			}
			List<Emplacement> emps = new ArrayList<>();
			for (String blocId : blocIds) {
				if (!seen.add(blocId)) {
					throw new ApiException(
						HttpStatus.BAD_REQUEST,
						"DUPLICATE_BLOC_ASSIGNMENT",
						"Le même bloc ne peut pas être attribué à deux boîtes du même bordereau."
					);
				}
				Emplacement emp = emplacementRepository.findById(blocId)
					.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "BLOC_NOT_FOUND", "Emplacement de bloc inconnu."));
				if (emp.getTypeEmp() != TypeEmp.BLOC) {
					throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EMPLACEMENT_TYPE", "Chaque emplacement proposé doit être un bloc.");
				}
				if (emplacementOccupationService.isBlocOccupiedByOtherBoite(blocId, null)) {
					throw new ApiException(
						HttpStatus.CONFLICT,
						"BLOC_OCCUPE",
						"Le bloc « " + emp.getNumero() + " » est déjà occupé par une autre boîte."
					);
				}
				emps.add(emp);
			}
			emps.sort(Comparator.comparing(Emplacement::getNumero));
			boolean acceptSplit = Boolean.TRUE.equals(br.acceptSplitBlocAssignment());
			boolean acceptMulti = Boolean.TRUE.equals(br.acceptMultiTabletteBlocAssignment());
			if (acceptSplit && acceptMulti) {
				throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"CONFLICT_ASSIGNMENT_ACCEPT_FLAGS",
					"Une seule option d’acceptation à la fois : deux segments sur la même tablette, ou plusieurs tablettes."
				);
			}
			if (acceptMulti) {
				if (!isValidMultiTabletteBlocSelection(emps)) {
					throw new ApiException(
						HttpStatus.BAD_REQUEST,
						"INVALID_MULTI_TABLETTE_BLOCS",
						"Affectation multi-tablettes : chaque segment doit être consécutif sur sa tablette, et les segments concernent des tablettes distinctes dans l’ordre croissant du numéro de tablette."
					);
				}
			}
			else if (acceptSplit) {
				if (!isValidBlocSelectionWithOptionalSplit(emps)) {
					throw new ApiException(
						HttpStatus.BAD_REQUEST,
						"INVALID_SPLIT_BLOCS",
						"Affectation en deux segments : une première plage issue des premières places libres contiguës, puis une deuxième après un ou plusieurs blocs déjà occupés, le tout sur une même tablette."
					);
				}
			}
			else if (!areConsecutiveBlocsOnTablette(emps)) {
				throw new ApiException(
					HttpStatus.BAD_REQUEST,
					"BLOCS_NOT_CONSECUTIVE",
					"Les blocs doivent être consécutifs sur une même tablette, ou confirmez une proposition en deux segments (même tablette) ou multi-tablettes si le système l’a indiquée."
				);
			}
		}
	}

	private List<String> resolveBlocIds(CreateBordereauBoiteRequest br) {
		if (br.emplacementBlocIds() != null && !br.emplacementBlocIds().isEmpty()) {
			return br.emplacementBlocIds().stream()
				.map(this::trimToNull)
				.filter(Objects::nonNull)
				.toList();
		}
		String single = trimToNull(br.emplacementBlocId());
		if (single == null) {
			return List.of();
		}
		return List.of(single);
	}

	private static String tablettePrefix(String blocNumero) {
		if (blocNumero == null || blocNumero.length() < 2) {
			return blocNumero == null ? "" : blocNumero;
		}
		return blocNumero.substring(0, blocNumero.length() - 1);
	}

	private static boolean areConsecutiveBlocsOnTablette(List<Emplacement> blocs) {
		if (blocs.isEmpty()) {
			return true;
		}
		String prefix = tablettePrefix(blocs.get(0).getNumero());
		int startSlot = blocs.get(0).getNumero().charAt(blocs.get(0).getNumero().length() - 1) - '1';
		for (int i = 0; i < blocs.size(); i++) {
			Emplacement e = blocs.get(i);
			String num = e.getNumero();
			if (!tablettePrefix(num).equals(prefix)) {
				return false;
			}
			int expectedSlot = startSlot + i;
			int actualSlot = num.charAt(num.length() - 1) - '1';
			if (actualSlot != expectedSlot) {
				return false;
			}
		}
		return true;
	}

	private record SplitBlocPick(List<Emplacement> combined, int premierePlageNombre) {
	}

	/**
	 * Sélection valide : soit {@code n} blocs consécutifs, soit exactement deux segments sur la même tablette
	 * (comme {@link #trySplitOnTabletteSortedFrees}) : au moins un slot occupé entre les deux plages.
	 */
	private boolean isValidBlocSelectionWithOptionalSplit(List<Emplacement> emps) {
		if (emps.isEmpty()) {
			return true;
		}
		List<Emplacement> sorted = new ArrayList<>(emps);
		sorted.sort(Comparator.comparing(Emplacement::getNumero));
		List<List<Emplacement>> runs = partitionConsecutiveRunsOnTablette(sorted);
		if (runs.size() == 1) {
			return areConsecutiveBlocsOnTablette(sorted);
		}
		if (runs.size() != 2) {
			return false;
		}
		List<Emplacement> r0 = runs.get(0);
		List<Emplacement> r1 = runs.get(1);
		String p0 = tablettePrefix(r0.get(0).getNumero());
		if (!tablettePrefix(r1.get(0).getNumero()).equals(p0)) {
			return false;
		}
		int slotLast0 = slotIndex(r0.get(r0.size() - 1));
		int slotFirst1 = slotIndex(r1.get(0));
		return slotFirst1 >= slotLast0 + 2;
	}

	/** Sélection sur plusieurs tablettes : au moins deux segments ; préfixes croissants ; chaque segment consécutif sur sa tablette. */
	private static boolean isValidMultiTabletteBlocSelection(List<Emplacement> sorted) {
		if (sorted.isEmpty()) {
			return true;
		}
		List<List<Emplacement>> runs = partitionConsecutiveRunsOnTablette(sorted);
		if (runs.size() < 2) {
			return false;
		}
		String prevPref = tablettePrefix(runs.get(0).get(0).getNumero());
		if (!areConsecutiveBlocsOnTablette(runs.get(0))) {
			return false;
		}
		for (int i = 1; i < runs.size(); i++) {
			List<Emplacement> r = runs.get(i);
			String p = tablettePrefix(r.get(0).getNumero());
			if (p.compareTo(prevPref) <= 0) {
				return false;
			}
			prevPref = p;
			if (!areConsecutiveBlocsOnTablette(r)) {
				return false;
			}
		}
		return true;
	}

	private static List<List<Emplacement>> partitionConsecutiveRunsOnTablette(List<Emplacement> sorted) {
		List<List<Emplacement>> runs = new ArrayList<>();
		for (Emplacement e : sorted) {
			if (runs.isEmpty()) {
				runs.add(new ArrayList<>(List.of(e)));
				continue;
			}
			List<Emplacement> last = runs.get(runs.size() - 1);
			Emplacement prev = last.get(last.size() - 1);
			if (tablettePrefix(prev.getNumero()).equals(tablettePrefix(e.getNumero())) && slotIndex(e) == slotIndex(prev) + 1) {
				last.add(e);
			}
			else {
				runs.add(new ArrayList<>(List.of(e)));
			}
		}
		return runs;
	}

	private static int slotIndex(Emplacement e) {
		String num = e.getNumero();
		return num.charAt(num.length() - 1) - '1';
	}

	/** Premier segment = plus long préfixe de blocs libres consécutifs (ordre NUMERO asc) sur cette tablette. */
	private List<Emplacement> leadingFreeConsecutivePrefixOfTablette(List<Emplacement> freesSortedOnTablette) {
		List<Emplacement> run = new ArrayList<>();
		for (Emplacement e : freesSortedOnTablette) {
			if (run.isEmpty()) {
				run.add(e);
				continue;
			}
			String p0 = tablettePrefix(run.get(0).getNumero());
			if (!tablettePrefix(e.getNumero()).equals(p0)) {
				break;
			}
			int prev = slotIndex(run.get(run.size() - 1));
			int cur = slotIndex(e);
			if (cur == prev + 1) {
				run.add(e);
			}
			else {
				break;
			}
		}
		return run;
	}

	private List<Emplacement> findFirstConsecutiveFreeWindowFromIndex(List<Emplacement> freesSorted, int fromIndexInclusive, int count) {
		if (count <= 0 || fromIndexInclusive > freesSorted.size() - count) {
			return List.of();
		}
		for (int i = fromIndexInclusive; i <= freesSorted.size() - count; i++) {
			List<Emplacement> candidate = freesSorted.subList(i, i + count);
			if (areConsecutiveBlocsOnTablette(candidate)) {
				return new ArrayList<>(candidate);
			}
		}
		return List.of();
	}

	private Optional<SplitBlocPick> trySplitOnTabletteSortedFrees(List<Emplacement> tabFreesSorted, int n) {
		if (n <= 1 || tabFreesSorted.isEmpty()) {
			return Optional.empty();
		}
		List<Emplacement> segA = leadingFreeConsecutivePrefixOfTablette(tabFreesSorted);
		int x = segA.size();
		if (x <= 0 || x >= n) {
			return Optional.empty();
		}
		int needB = n - x;
		List<Emplacement> segB = findFirstConsecutiveFreeWindowFromIndex(tabFreesSorted, x, needB);
		if (segB.size() != needB) {
			return Optional.empty();
		}
		int slotLastA = slotIndex(segA.get(segA.size() - 1));
		int slotFirstB = slotIndex(segB.get(0));
		if (slotFirstB < slotLastA + 2) {
			return Optional.empty();
		}
		ArrayList<Emplacement> all = new ArrayList<>(segA);
		all.addAll(segB);
		return Optional.of(new SplitBlocPick(all, x));
	}

	private Optional<SplitBlocPick> findSplitTwoSegmentAssignment(List<Emplacement> pool, int n, Set<String> reserved) {
		if (n <= 1) {
			return Optional.empty();
		}
		Map<String, List<Emplacement>> byTablette = new LinkedHashMap<>();
		for (Emplacement e : pool) {
			if (reserved.contains(e.getId())) {
				continue;
			}
			byTablette.computeIfAbsent(tablettePrefix(e.getNumero()), k -> new ArrayList<>()).add(e);
		}
		for (List<Emplacement> tabBlocs : byTablette.values()) {
			tabBlocs.sort(Comparator.comparing(Emplacement::getNumero));
			Optional<SplitBlocPick> p = trySplitOnTabletteSortedFrees(tabBlocs, n);
			if (p.isPresent()) {
				return p;
			}
		}
		return Optional.empty();
	}

	/**
	 * Autre tablette après la première : le plus long préfixe consécutif libre ({@code x &lt; n}) sur une tablette
	 * puis {@code n - x} blocs consécutifs sur une tablette ultérieure (ordre lexical du préfixe tablette).
	 */
	private Optional<SplitBlocPick> findCrossTabletteAssignment(List<Emplacement> pool, int n, Set<String> reserved) {
		if (n <= 1) {
			return Optional.empty();
		}
		Map<String, List<Emplacement>> byTablette = new LinkedHashMap<>();
		for (Emplacement e : pool) {
			if (reserved.contains(e.getId())) {
				continue;
			}
			byTablette.computeIfAbsent(tablettePrefix(e.getNumero()), k -> new ArrayList<>()).add(e);
		}
		List<String> prefixes = new ArrayList<>(byTablette.keySet());
		prefixes.sort(Comparator.naturalOrder());
		for (String p1 : prefixes) {
			List<Emplacement> tab1 = byTablette.get(p1);
			tab1.sort(Comparator.comparing(Emplacement::getNumero));
			List<Emplacement> segA = leadingFreeConsecutivePrefixOfTablette(tab1);
			int x = segA.size();
			if (x <= 0 || x >= n) {
				continue;
			}
			int need = n - x;
			for (String p2 : prefixes) {
				if (p2.compareTo(p1) <= 0) {
					continue;
				}
				List<Emplacement> tab2 = new ArrayList<>(byTablette.get(p2));
				tab2.sort(Comparator.comparing(Emplacement::getNumero));
				List<Emplacement> segB = findFirstConsecutiveFreeWindowFromIndex(tab2, 0, need);
				if (segB.size() == need) {
					ArrayList<Emplacement> combined = new ArrayList<>(segA);
					combined.addAll(segB);
					return Optional.of(new SplitBlocPick(combined, x));
				}
			}
		}
		return Optional.empty();
	}

	/** Premier groupe de {@code count} blocs libres consécutifs sur une tablette (ordre métier global). */
	private List<Emplacement> findConsecutiveFreeBlocs(List<Emplacement> pool, int count, Set<String> reserved) {
		if (count <= 0) {
			return List.of();
		}
		Map<String, List<Emplacement>> byTablette = new LinkedHashMap<>();
		for (Emplacement e : pool) {
			if (reserved.contains(e.getId())) {
				continue;
			}
			byTablette.computeIfAbsent(tablettePrefix(e.getNumero()), k -> new ArrayList<>()).add(e);
		}
		for (List<Emplacement> tabBlocs : byTablette.values()) {
			tabBlocs.sort(Comparator.comparing(Emplacement::getNumero));
			for (int i = 0; i <= tabBlocs.size() - count; i++) {
				List<Emplacement> candidate = tabBlocs.subList(i, i + count);
				if (areConsecutiveBlocsOnTablette(candidate)) {
					return new ArrayList<>(candidate);
				}
			}
		}
		return List.of();
	}

	private String trimToNull(String s) {
		if (s == null) {
			return null;
		}
		String t = s.trim();
		return t.isEmpty() ? null : t;
	}

	private BordereauListItemResponse toListItem(Bordereau b, boolean regleDureeActiveInconnue, String reglesEnAlerteResume) {
		Direction d = b.getDirection();
		return new BordereauListItemResponse(
			b.getId(),
			BordereauNumeroSupport.displayNumero(b),
			b.getDateTransfert().toString(),
			d == null ? null : d.getId(),
			d == null ? null : d.getLabel(),
			b.getAgent().getUserName(),
			resolveNombreBoites(b),
			b.getObservation(),
			regleDureeActiveInconnue,
			reglesEnAlerteResume
		);
	}

	private BordereauDetailResponse toDetail(Bordereau b) {
		Direction d = b.getDirection();
		boolean bordereauEnAttente = b.getStatut() == BordereauStatut.EN_ATTENTE;
		RegleAlertSummary regleAlert = regleAlertSummaryForBordereau(b);
		List<Boite> boitesEntities = b.getId() == null
			? List.of()
			: boiteRepository.findWithDocumentTypeByBordereauId(b.getId());
		List<BoiteResponse> boites = boitesEntities.stream()
			.map(box -> toBoiteResponse(box, bordereauEnAttente))
			.toList();
		return new BordereauDetailResponse(
			b.getId(),
			BordereauNumeroSupport.displayNumero(b),
			b.getDateTransfert().toString(),
			d == null ? null : d.getId(),
			d == null ? null : d.getLabel(),
			b.getAgent().getUserName(),
			b.getObservation(),
			b.getStatut().name(),
			regleAlert.regleDureeActiveInconnue(),
			regleAlert.reglesEnAlerteResume(),
			resolveNombreBoites(b),
			boites
		);
	}

	/** Valeur stockée sur {@code BORDEREAUX.NOMBRE_BOITES} ; repli sur {@code COUNT(*)} si la colonne est encore vide. */
	private int resolveNombreBoites(Bordereau b) {
		if (b.getNombreBoites() > 0) {
			return b.getNombreBoites();
		}
		if (b.getId() == null) {
			return 0;
		}
		return (int) boiteRepository.countByBordereauId(b.getId());
	}


	private BoiteResponse toBoiteResponse(Boite box, boolean bordereauEnAttente) {
		EtatCourantFields etat = resolveEtatCourantFields(box);
		if (bordereauEnAttente) {
			return new BoiteResponse(
				box.getId(),
				box.getTitre(),
				box.getAnneeMin(),
				box.getAnneeMax(),
				box.getMetrageCm(),
				box.getContenu(),
				box.getMotsCles(),
				box.getDocumentType().getId(),
				box.getDocumentType().getTitle(),
				null,
				null,
				null,
				null,
				null,
				etat.typeEtat(),
				etat.typeEtatLabel(),
				etat.dateEtat()
			);
		}
		EmplacementOccupationService.OccupationPlage occupation = box.getId() == null
			? new EmplacementOccupationService.OccupationPlage(null, null, null, null)
			: emplacementOccupationService.resolvePlage(box.getId());
		String debutId = occupation.debutId();
		String finId = occupation.finId();
		String plage = occupation.plage() == null ? null : EmplacementPlageFormatter.formatPlage(occupation.plage());
		String blocNumerosListe = occupation.numerosListe() != null && !occupation.numerosListe().isBlank()
			? occupation.numerosListe()
			: plage;
		return new BoiteResponse(
			box.getId(),
			box.getTitre(),
			box.getAnneeMin(),
			box.getAnneeMax(),
			box.getMetrageCm(),
			box.getContenu(),
			box.getMotsCles(),
			box.getDocumentType().getId(),
			box.getDocumentType().getTitle(),
			debutId,
			finId,
			plage,
			debutId,
			blocNumerosListe,
			etat.typeEtat(),
			etat.typeEtatLabel(),
			etat.dateEtat()
		);
	}

	private record EtatCourantFields(String typeEtat, String typeEtatLabel, String dateEtat) {
	}

	private static EtatCourantFields resolveEtatCourantFields(Boite box) {
		var etat = box.getEtatCourant();
		if (etat == null || etat.getTypeEtat() == null) {
			return new EtatCourantFields(null, null, null);
		}
		String date = etat.getDateEtat() == null ? null : etat.getDateEtat().toString();
		return new EtatCourantFields(
			etat.getTypeEtat().name(),
			etat.getTypeEtat().displayLabel(),
			date
		);
	}

	private enum CreatorFilter {
		TOUS,
		AGENT_UNIQUEMENT,
		ADMIN_UNIQUEMENT
	}

	private Specification<Bordereau> listSpec(
		boolean admin,
		String userAccountId,
		String userDirectionId,
		String q,
		BordereauStatut statut,
		CreatorFilter creatorFilter
	) {
		return listSpec(admin, userAccountId, userDirectionId, q, statut, creatorFilter, null, null, null);
	}

	private Specification<Bordereau> listSpec(
		boolean admin,
		String userAccountId,
		String userDirectionId,
		String q,
		BordereauStatut statut,
		CreatorFilter creatorFilter,
		String directionId,
		Integer anneeMin,
		Integer anneeMax
	) {
		return (root, query, cb) -> {
			if (Bordereau.class.equals(query.getResultType())) {
				root.fetch("agent", JoinType.INNER);
				root.fetch("direction", JoinType.LEFT);
			}
			List<Predicate> preds = new ArrayList<>();
			preds.add(cb.equal(root.get("statut"), statut));
			if (!admin) {
				preds.add(authorization.buildBordereauVisibilityPredicate(root, cb, userAccountId, userDirectionId, statut));
			}
			if (creatorFilter == CreatorFilter.AGENT_UNIQUEMENT) {
				Join<Bordereau, UserAccount> agentJoin = root.join("agent");
				preds.add(agentRoleIsNotAdmin(agentJoin, cb));
			} else if (creatorFilter == CreatorFilter.ADMIN_UNIQUEMENT) {
				Join<Bordereau, UserAccount> agentJoin = root.join("agent");
				preds.add(creatorRoleIsAdmin(agentJoin, cb));
			}
			if (directionId != null && !directionId.isEmpty()) {
				Join<Bordereau, Direction> dirFilterJoin = root.join("direction", JoinType.INNER);
				preds.add(cb.equal(dirFilterJoin.get("id"), directionId));
			}
			if (anneeMin != null) {
				preds.add(cb.greaterThanOrEqualTo(root.get("anneeNumero"), anneeMin));
			}
			if (anneeMax != null) {
				preds.add(cb.lessThanOrEqualTo(root.get("anneeNumero"), anneeMax));
			}
			if (q != null && !q.isEmpty()) {
				String qLower = q.toLowerCase();
				String like = "%" + qLower + "%";
				List<Predicate> searchOr = new ArrayList<>();
				searchOr.add(cb.like(cb.lower(root.get("numeroAffiche")), like));
				searchOr.add(cb.like(cb.lower(cb.coalesce(root.get("observation"), cb.literal(""))), like));
				Subquery<Long> boxSq = query.subquery(Long.class);
				Root<Boite> boxRoot = boxSq.from(Boite.class);
				boxSq.select(boxRoot.get("id"));
				boxSq.where(
					cb.equal(boxRoot.get("bordereau"), root),
					cb.like(cb.lower(cb.coalesce(boxRoot.get("motsCles"), cb.literal(""))), like)
				);
				searchOr.add(cb.exists(boxSq));
				preds.add(cb.or(searchOr.toArray(Predicate[]::new)));
			}
			return cb.and(preds.toArray(Predicate[]::new));
		};
	}

	/** Créateur non administrateur (agent métier). */
	private static Predicate agentRoleIsNotAdmin(
		Join<Bordereau, UserAccount> agentJoin,
		jakarta.persistence.criteria.CriteriaBuilder cb
	) {
		return cb.not(creatorRoleIsAdmin(agentJoin, cb));
	}

	/** Créateur administrateur (colonne ROLE : admin, role_admin, etc.). */
	private static Predicate creatorRoleIsAdmin(
		Join<Bordereau, UserAccount> agentJoin,
		jakarta.persistence.criteria.CriteriaBuilder cb
	) {
		var roleLower = cb.lower(agentJoin.get("role"));
		return cb.or(
			cb.equal(roleLower, "admin"),
			cb.equal(roleLower, "role_admin"),
			cb.like(roleLower, "admin,%"),
			cb.like(roleLower, "%,admin,%"),
			cb.like(roleLower, "%,admin")
		);
	}

	private String resolveDirectionLabel(String directionId) {
		if (directionId == null || directionId.isBlank()) {
			return null;
		}
		return directionRepository.findById(directionId)
			.map(Direction::getLabel)
			.orElse(directionId);
	}

	/**
	 * Agent : impose la direction de son profil ; administrateur : choix libre (y compris aucune).
	 */
	private Direction resolveDirectionForWrite(Authentication authentication, String requestedDirectionId) {
		String requested = requestedDirectionId == null ? "" : requestedDirectionId.trim();
		if (!authorization.isAdmin(authentication)) {
			String userDirId = authorization.resolveUserDirectionId(authentication.getName());
			if (userDirId != null && !userDirId.isBlank()) {
				if (!requested.isEmpty() && !userDirId.equals(requested)) {
					throw new ApiException(
						HttpStatus.FORBIDDEN,
						"DIRECTION_LOCKED",
						"La direction du bordereau est celle de votre profil agent."
					);
				}
				return directionRepository.findById(userDirId)
					.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_DIRECTION", "Direction agent introuvable."));
			}
			if (!requested.isEmpty()) {
				return directionRepository.findById(requested)
					.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_DIRECTION", "Direction inconnue."));
			}
			return null;
		}
		if (requested.isEmpty()) {
			return null;
		}
		return directionRepository.findById(requested)
			.orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_DIRECTION", "Direction inconnue."));
	}

	/** Bordereau {@code EN_ATTENTE} : aucune occupation physique sur les blocs (réparation si incohérent). */
	private void ensureEnAttenteBordereauSansEmplacement(Bordereau b) {
		if (b == null || b.getId() == null || b.getStatut() != BordereauStatut.EN_ATTENTE) {
			return;
		}
		List<Boite> boxes = boiteRepository.findByBordereauId(b.getId());
		boolean dirty = false;
		for (Boite box : boxes) {
			if (box.getId() != null && emplacementOccupationService.hasBlocAssignment(box.getId())) {
				boiteEtatService.libererEmplacementsPhysiques(box);
				dirty = true;
			}
		}
		if (dirty) {
			boiteRepository.saveAll(boxes);
		}
	}

	private static boolean hasEmplacementAssignmentsInRequest(CreateBordereauRequest request) {
		if (request.boites() == null) {
			return false;
		}
		for (CreateBordereauBoiteRequest br : request.boites()) {
			if (br.emplacementBlocIds() != null && !br.emplacementBlocIds().isEmpty()) {
				return true;
			}
			String single = br.emplacementBlocId();
			if (single != null && !single.isBlank()) {
				return true;
			}
		}
		return false;
	}
}
