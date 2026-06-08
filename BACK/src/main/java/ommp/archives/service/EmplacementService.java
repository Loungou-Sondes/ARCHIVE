package ommp.archives.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import ommp.archives.dto.emplacement.BlocBoiteSummaryDto;
import ommp.archives.dto.emplacement.BlocDto;
import ommp.archives.dto.emplacement.BoiteBordereauNumeroProjection;
import ommp.archives.dto.emplacement.CreateEpiRequest;
import ommp.archives.dto.emplacement.EpiBulkImportResult;
import ommp.archives.dto.emplacement.EpiListPageResponse;
import ommp.archives.dto.emplacement.EpiMatrixDto;
import ommp.archives.dto.emplacement.EpiSummaryDto;
import ommp.archives.dto.emplacement.NextEpiNumeroDto;
import ommp.archives.dto.emplacement.ResizeEpiRequest;
import ommp.archives.dto.emplacement.TabletteCellDto;
import ommp.archives.dto.emplacement.TabletteFillAlertDto;
import ommp.archives.dto.emplacement.TabletteFillAlertPageResponse;
import ommp.archives.dto.emplacement.TraversHeaderDto;
import ommp.archives.entity.Bordereau;
import ommp.archives.entity.Boite;
import ommp.archives.entity.Emplacement;
import ommp.archives.entity.TypeEmp;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.BoiteRepository;
import ommp.archives.repository.EmplacementRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class EmplacementService {

	private static final Logger log = LoggerFactory.getLogger(EmplacementService.class);

	private static final int MAX_DIM = 9;
	private static final int MAX_BLOCS_TABLETTE = 8;

	private record GridDims(int cols, int rows, int bp, int cm) {
	}

	private record BoiteClusterContext(
		Map<Long, Boite> boitesById,
		Map<Long, String> bordereauNumeroAfficheByBoiteId,
		Map<Long, Integer> bordereauBoiteCountByBordereauId,
		Map<Long, Boolean> bordereauBoitesNonContiguesByBordereauId
	) {
		private static BoiteClusterContext empty() {
			return new BoiteClusterContext(Map.of(), Map.of(), Map.of(), Map.of());
		}
	}

	private final EmplacementRepository emplacementRepository;
	private final BoiteRepository boiteRepository;
	private final EpiExcelImportService epiExcelImportService;
	private final AuthorizationService authorization;

	public EmplacementService(
		EmplacementRepository emplacementRepository,
		BoiteRepository boiteRepository,
		EpiExcelImportService epiExcelImportService,
		AuthorizationService authorization
	) {
		this.emplacementRepository = emplacementRepository;
		this.boiteRepository = boiteRepository;
		this.epiExcelImportService = epiExcelImportService;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public EpiListPageResponse listEpis(Authentication authentication, String q, Pageable pageable) {
		authorization.requireAdmin(authentication);
		String qTrim = trimToNull(q);
		List<Emplacement> allRoots = emplacementRepository.findEpisRootsOrderByCreatedAtAsc();
		Set<String> rootNumeros = allRoots.stream()
			.map(e -> safeEpi(e.getNumero()))
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toSet());

		List<Emplacement> filtered = allRoots.stream()
			.filter(epi -> qTrim == null || safeEpi(epi.getNumero()).toLowerCase().contains(qTrim.toLowerCase()))
			.toList();

		int pageSize = pageable.getPageSize() > 0 ? pageable.getPageSize() : 12;
		int page = Math.max(0, pageable.getPageNumber());
		int start = page * pageSize;
		int end = Math.min(start + pageSize, filtered.size());
		List<Emplacement> pageRoots = start >= filtered.size() ? List.of() : filtered.subList(start, end);

		List<EpiSummaryDto> content = pageRoots.stream()
			.map(epi -> toSummary(epi.getNumero(), loadCluster(epi.getNumero(), rootNumeros), null))
			.toList();

		long totalLinearCm = filtered.stream()
			.mapToLong(epi -> Math.round(epi.getMetrage() == null ? 0.0 : epi.getMetrage()))
			.sum();
		int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) filtered.size() / pageSize);

		return new EpiListPageResponse(
			content,
			filtered.size(),
			totalPages,
			page,
			pageSize,
			totalLinearCm
		);
	}

	@Transactional(readOnly = true)
	public NextEpiNumeroDto previewNextNumero(Authentication authentication) {
		authorization.requireAdmin(authentication);
		return new NextEpiNumeroDto(computeNextAutoNumero());
	}

	public byte[] buildEpiImportTemplate(Authentication authentication) {
		authorization.requireAdmin(authentication);
		return epiExcelImportService.buildTemplateWorkbook();
	}

	@Transactional
	public EpiBulkImportResult importEpisFromExcel(Authentication authentication, MultipartFile file) {
		authorization.requireAdmin(authentication);
		List<CreateEpiRequest> rows = epiExcelImportService.parseAndValidate(file);
		List<String> numeros = new ArrayList<>();
		for (CreateEpiRequest row : rows) {
			validateEpiDimensions(row.traversCount(), row.tabletteRows(), row.blocsPerTablette(), row.blocLinearCm());
			String numero = computeNextAutoNumero();
			persistNewEpi(numero, row.traversCount(), row.tabletteRows(), row.blocsPerTablette(), row.blocLinearCm());
			numeros.add(numero);
		}
		return new EpiBulkImportResult(numeros.size(), numeros);
	}

	@Transactional
	public EpiSummaryDto createEpi(Authentication authentication, CreateEpiRequest req) {
		authorization.requireAdmin(authentication);
		validateEpiDimensions(req.traversCount(), req.tabletteRows(), req.blocsPerTablette(), req.blocLinearCm());
		String numero = computeNextAutoNumero();
		persistNewEpi(numero, req.traversCount(), req.tabletteRows(), req.blocsPerTablette(), req.blocLinearCm());
		String label = req.typeLabel() != null && !req.typeLabel().isBlank() ? req.typeLabel().trim() : null;
		return toSummary(numero, loadCluster(numero), label);
	}

	private static void validateEpiDimensions(int cols, int rows, int blocs, int cm) {
		if (cols < 1 || cols > MAX_DIM || rows < 1 || rows > MAX_DIM) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRID", "Dimensions entre 1 et 9.");
		}
		if (blocs < 1 || blocs > MAX_BLOCS_TABLETTE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BLOCS", "De 1 à 8 blocs par tablette.");
		}
		if (cm < 1 || cm > 500) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BLOC_CM", "Métrage bloc entre 1 et 500 cm.");
		}
	}

	private void persistNewEpi(String numero, int cols, int rows, int blocs, int cm) {
		List<Emplacement> batch = new ArrayList<>();
		batch.add(newEmplacement(TypeEmp.EPI, numero, computeEpiMetrage(cols, rows, blocs, cm)));
		for (int c = 0; c < cols; c++) {
			batch.add(newEmplacement(TypeEmp.TRAVEE, codeTravers(numero, c), computeTraveeMetrage(rows, blocs, cm)));
			for (int r = 0; r < rows; r++) {
				batch.add(newEmplacement(TypeEmp.TABLETTE, codeTablette(numero, c, r), computeTabletteMetrage(blocs, cm)));
				for (int i = 0; i < blocs; i++) {
					batch.add(newEmplacement(TypeEmp.BLOC, codeBloc(numero, c, r, i), (double) cm));
				}
			}
		}
		emplacementRepository.saveAll(batch);
	}

	private static Emplacement newEmplacement(TypeEmp type, String numero, Double metrage) {
		Emplacement e = new Emplacement();
		e.setId(UUID.randomUUID().toString());
		e.setTypeEmp(type);
		e.setNumero(numero);
		e.setMetrage(metrage);
		return e;
	}

	private static double computeEpiMetrage(int cols, int rows, int bp, int cm) {
		return (double) cols * rows * bp * cm;
	}

	private static double computeTraveeMetrage(int rows, int bp, int cm) {
		return (double) rows * bp * cm;
	}

	private static double computeTabletteMetrage(int bp, int cm) {
		return (double) bp * cm;
	}

	private String computeNextAutoNumero() {
		Set<String> taken = emplacementRepository.findByTypeEmp(TypeEmp.EPI).stream()
			.map(Emplacement::getNumero)
			.filter(Objects::nonNull)
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toCollection(HashSet::new));
		for (int n = 1; n < 10_000; n++) {
			String candidate = formatAutoNumero(n);
			if (taken.contains(candidate)) {
				continue;
			}
			if (!isPrefixFreeAmongEpis(candidate, taken)) {
				continue;
			}
			return candidate;
		}
		throw new ApiException(HttpStatus.BAD_REQUEST, "NUMERO_POOL_EXHAUSTED", "Impossible d'attribuer un nouveau numéro d'épi.");
	}

	/** Aucun épi existant ne doit être préfixe strict de {@code candidate}, ni l’inverse (sinon collision d’arbres par préfixe). */
	private static boolean isPrefixFreeAmongEpis(String candidate, Set<String> existingEpiNumeros) {
		for (String p : existingEpiNumeros) {
			if (p.startsWith(candidate) && !p.equals(candidate)) {
				return false;
			}
			if (candidate.startsWith(p) && !candidate.equals(p)) {
				return false;
			}
		}
		return true;
	}

	private static String formatAutoNumero(int n) {
		if (n >= 1 && n <= 99) {
			return String.format("%02d", n);
		}
		return String.valueOf(n);
	}

	@Transactional
	public EpiSummaryDto deleteLastEpi(Authentication authentication) {
		authorization.requireAdmin(authentication);
		Emplacement last = emplacementRepository.findLastEpiRoot()
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NO_EPI", "Aucun épi à supprimer."));
		String prefix = safeEpi(last.getNumero());
		List<Emplacement> cluster = loadCluster(prefix);
		releaseOrphanedBoiteIds(cluster);
		assertEmplacementsDeletable(cluster, EmplacementDeletionScope.EPI, prefix);
		EpiSummaryDto dto = toSummary(prefix, cluster, null);
		emplacementRepository.deleteAll(cluster);
		return dto;
	}

	@Transactional
	public EpiSummaryDto resizeEpi(Authentication authentication, String epiId, ResizeEpiRequest req) {
		authorization.requireAdmin(authentication);
		Emplacement epi = loadEpiRoot(epiId);
		String epiNum = safeEpi(epi.getNumero());
		int targetCols = req.traversCount();
		int targetRows = req.tabletteRows();
		int targetBp = req.blocsPerTablette();
		if (targetCols < 1 || targetCols > MAX_DIM || targetRows < 1 || targetRows > MAX_DIM) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRID", "Dimensions entre 1 et 9.");
		}
		if (targetBp < 1 || targetBp > MAX_BLOCS_TABLETTE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_BLOCS", "De 1 à 8 blocs par tablette.");
		}

		GridDims current = resolveDims(loadCluster(epiNum), epiNum);
		if (targetCols < current.cols() || targetRows < current.rows() || targetBp < current.bp()) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"RESIZE_SHRINK_FORBIDDEN",
				"Seul l'agrandissement de l'épi est autorisé (aucune dimension ne peut être inférieure à la valeur actuelle)."
			);
		}

		while (true) {
			GridDims d = resolveDims(loadCluster(epiNum), epiNum);
			if (d.cols() >= targetCols) {
				break;
			}
			if (d.cols() >= MAX_DIM) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "MAX_COLS", "Maximum 9 travées.");
			}
			applyAddTraversColumn(epiNum);
		}
		while (true) {
			GridDims d = resolveDims(loadCluster(epiNum), epiNum);
			if (d.rows() >= targetRows) {
				break;
			}
			if (d.rows() >= MAX_DIM) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "MAX_ROWS", "Maximum 9 lignes de tablettes.");
			}
			applyAddTabletteRow(epiNum);
		}
		while (true) {
			GridDims d = resolveDims(loadCluster(epiNum), epiNum);
			if (d.bp() >= targetBp) {
				break;
			}
			if (d.bp() >= MAX_BLOCS_TABLETTE) {
				throw new ApiException(HttpStatus.BAD_REQUEST, "MAX_BLOCS", "Maximum 8 blocs par tablette.");
			}
			applyAddLastBlocSlot(epiNum);
		}
		return toSummary(epiNum, loadCluster(epiNum), null);
	}

	@Transactional
	public EpiMatrixDto getEpiMatrix(Authentication authentication, String epiId) {
		authorization.requireAdmin(authentication);
		try {
			Emplacement epi = loadEpiRoot(epiId);
			String epiNum = safeEpi(epi.getNumero());
			releaseOrphanedBoiteIds(loadCluster(epiNum));
			return buildMatrixDto(epi);
		} catch (ApiException ex) {
			throw ex;
		} catch (RuntimeException ex) {
			log.error("Erreur lors du chargement de la matrice de l’épi {}", epiId, ex);
			String origin = "";
			StackTraceElement[] st = ex.getStackTrace();
			for (StackTraceElement el : st) {
				if (el.getClassName().startsWith("ommp.archives")) {
					origin = " @ " + el.getClassName().substring(el.getClassName().lastIndexOf('.') + 1)
						+ "." + el.getMethodName() + ":" + el.getLineNumber();
					break;
				}
			}
			String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getClass().getSimpleName() + " — " + ex.getMessage();
			throw new ApiException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"MATRIX_LOAD_ERROR",
				"Impossible de charger la matrice : " + msg + origin
			);
		}
	}

	@Transactional
	public EpiMatrixDto addTabletteRow(Authentication authentication, String epiId) {
		authorization.requireAdmin(authentication);
		Emplacement epi = loadEpiRoot(epiId);
		String epiNum = safeEpi(epi.getNumero());
		applyAddTabletteRow(epiNum);
		return buildMatrixDto(loadEpiRoot(epiId));
	}

	@Transactional
	public EpiMatrixDto removeLastTabletteRow(Authentication authentication, String epiId) {
		authorization.requireAdmin(authentication);
		Emplacement epi = loadEpiRoot(epiId);
		String epiNum = safeEpi(epi.getNumero());
		applyRemoveLastTabletteRow(epiNum);
		return buildMatrixDto(loadEpiRoot(epiId));
	}

	@Transactional
	public EpiMatrixDto addTraversColumn(Authentication authentication, String epiId) {
		authorization.requireAdmin(authentication);
		Emplacement epi = loadEpiRoot(epiId);
		String epiNum = safeEpi(epi.getNumero());
		applyAddTraversColumn(epiNum);
		return buildMatrixDto(loadEpiRoot(epiId));
	}

	@Transactional
	public EpiMatrixDto removeLastTraversColumn(Authentication authentication, String epiId) {
		authorization.requireAdmin(authentication);
		Emplacement epi = loadEpiRoot(epiId);
		String epiNum = safeEpi(epi.getNumero());
		applyRemoveLastTraversColumn(epiNum);
		return buildMatrixDto(loadEpiRoot(epiId));
	}

	private void applyAddTabletteRow(String epiNum) {
		List<Emplacement> cluster = loadCluster(epiNum);
		GridDims d = resolveDims(cluster, epiNum);
		if (d.rows() >= MAX_DIM) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "MAX_ROWS", "Maximum 9 lignes de tablettes.");
		}
		int newRow = d.rows();
		List<Emplacement> add = new ArrayList<>();
		for (int c = 0; c < d.cols(); c++) {
			add.add(newEmplacement(TypeEmp.TABLETTE, codeTablette(epiNum, c, newRow), computeTabletteMetrage(d.bp(), d.cm())));
			for (int i = 0; i < d.bp(); i++) {
				add.add(newEmplacement(TypeEmp.BLOC, codeBloc(epiNum, c, newRow, i), (double) d.cm()));
			}
		}
		emplacementRepository.saveAll(add);
		recalcAndSaveCluster(epiNum);
	}

	private void applyRemoveLastTabletteRow(String epiNum) {
		List<Emplacement> cluster = loadCluster(epiNum);
		GridDims d = resolveDims(cluster, epiNum);
		if (d.rows() <= 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "MIN_ROWS", "Il doit rester au moins une ligne de tablettes.");
		}
		int lastRow = d.rows() - 1;
		List<Emplacement> toRemove = new ArrayList<>();
		for (int c = 0; c < d.cols(); c++) {
			String tab = codeTablette(epiNum, c, lastRow);
			toRemove.addAll(emplacementRepository.findByNumeroStartingWith(tab));
		}
		releaseOrphanedBoiteIds(toRemove);
		assertEmplacementsDeletable(toRemove, EmplacementDeletionScope.LAST_TABLETTE_ROW, epiNum);
		emplacementRepository.deleteAll(toRemove);
		recalcAndSaveCluster(epiNum);
	}

	private void applyAddTraversColumn(String epiNum) {
		List<Emplacement> cluster = loadCluster(epiNum);
		GridDims d = resolveDims(cluster, epiNum);
		if (d.cols() >= MAX_DIM) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "MAX_COLS", "Maximum 9 travées.");
		}
		int newCol = d.cols();
		List<Emplacement> add = new ArrayList<>();
		add.add(newEmplacement(TypeEmp.TRAVEE, codeTravers(epiNum, newCol), computeTraveeMetrage(d.rows(), d.bp(), d.cm())));
		for (int r = 0; r < d.rows(); r++) {
			add.add(newEmplacement(TypeEmp.TABLETTE, codeTablette(epiNum, newCol, r), computeTabletteMetrage(d.bp(), d.cm())));
			for (int i = 0; i < d.bp(); i++) {
				add.add(newEmplacement(TypeEmp.BLOC, codeBloc(epiNum, newCol, r, i), (double) d.cm()));
			}
		}
		emplacementRepository.saveAll(add);
		recalcAndSaveCluster(epiNum);
	}

	private void applyRemoveLastTraversColumn(String epiNum) {
		List<Emplacement> cluster = loadCluster(epiNum);
		GridDims d = resolveDims(cluster, epiNum);
		if (d.cols() <= 1) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "MIN_COLS", "Il doit rester au moins une travée.");
		}
		int lastCol = d.cols() - 1;
		String travPrefix = codeTravers(epiNum, lastCol);
		List<Emplacement> toRemove = emplacementRepository.findByNumeroStartingWith(travPrefix);
		releaseOrphanedBoiteIds(toRemove);
		assertEmplacementsDeletable(toRemove, EmplacementDeletionScope.LAST_TRAVEE, epiNum);
		emplacementRepository.deleteAll(toRemove);
		recalcAndSaveCluster(epiNum);
	}

	private void applyAddLastBlocSlot(String epiNum) {
		List<Emplacement> cluster = loadCluster(epiNum);
		GridDims d = resolveDims(cluster, epiNum);
		if (d.bp() >= MAX_BLOCS_TABLETTE) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "MAX_BLOCS", "Maximum 8 blocs par tablette.");
		}
		if (d.cols() <= 0 || d.rows() <= 0) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_GRID", "Grille d'épi invalide.");
		}
		int newSlot = d.bp();
		List<Emplacement> add = new ArrayList<>();
		for (int c = 0; c < d.cols(); c++) {
			for (int r = 0; r < d.rows(); r++) {
				add.add(newEmplacement(TypeEmp.BLOC, codeBloc(epiNum, c, r, newSlot), (double) d.cm()));
			}
		}
		emplacementRepository.saveAll(add);
		recalcAndSaveCluster(epiNum);
	}

	private Emplacement loadEpiRoot(String epiId) {
		Emplacement epi = emplacementRepository.findById(epiId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EPI_NOT_FOUND", "Épi introuvable."));
		if (epi.getTypeEmp() != TypeEmp.EPI) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_EPI", "Identifiant d'épi invalide.");
		}
		return epi;
	}

	private List<Emplacement> loadCluster(String epiNum) {
		return loadCluster(epiNum, null);
	}

	private List<Emplacement> loadCluster(String epiNum, Set<String> cachedRootNumeros) {
		String r = safeEpi(epiNum);
		Set<String> rootNumeros = cachedRootNumeros != null
			? cachedRootNumeros
			: emplacementRepository.findByTypeEmp(TypeEmp.EPI).stream()
				.map(e -> safeEpi(e.getNumero()))
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());
		return emplacementRepository.findByNumeroStartingWith(r).stream()
			.filter(e -> longestMatchingEpiRoot(e.getNumero(), rootNumeros).equals(r))
			.toList();
	}

	/**
	 * Parmi les numéros d’épis racines, retourne le plus long {@code P} tel que {@code numero} commence par {@code P}
	 * (évite qu’un arbre « 10 » absorbe l’épi « 100 » si les deux existent).
	 */
	private static String longestMatchingEpiRoot(String numero, Set<String> roots) {
		if (numero == null) {
			return "";
		}
		String best = "";
		for (String root : roots) {
			if (root.isEmpty()) {
				continue;
			}
			if (numero.startsWith(root) && root.length() > best.length()) {
				best = root;
			}
		}
		return best;
	}

	private void recalcAndSaveCluster(String epiNum) {
		String ep = safeEpi(epiNum);
		List<Emplacement> c = loadCluster(ep);
		if (c.isEmpty()) {
			return;
		}
		GridDims d = resolveDims(c, ep);
		for (Emplacement e : c) {
			switch (e.getTypeEmp()) {
				case EPI -> e.setMetrage(computeEpiMetrage(d.cols(), d.rows(), d.bp(), d.cm()));
				case TRAVEE -> e.setMetrage(computeTraveeMetrage(d.rows(), d.bp(), d.cm()));
				case TABLETTE -> e.setMetrage(computeTabletteMetrage(d.bp(), d.cm()));
				case BLOC -> e.setMetrage((double) d.cm());
			}
		}
		emplacementRepository.saveAll(c);
	}

	private GridDims resolveDims(List<Emplacement> cluster, String epiNum) {
		String ep = safeEpi(epiNum);
		int el = ep.length();
		int cols = (int) cluster.stream()
			.filter(e -> e.getTypeEmp() == TypeEmp.TRAVEE && e.getNumero().startsWith(ep) && e.getNumero().length() == el + 1)
			.count();
		int rows = cluster.stream()
			.filter(e -> e.getTypeEmp() == TypeEmp.TABLETTE && e.getNumero().startsWith(ep) && e.getNumero().length() == el + 2)
			.mapToInt(e -> parseTabletteRow(ep, e.getNumero()))
			.max()
			.orElse(-1) + 1;
		if (rows < 0) {
			rows = 0;
		}
		int bp = inferBlocsPerTablette(cluster, ep, el, cols, rows);
		int cm = inferBlocCm(cluster);
		return new GridDims(cols, rows, bp, cm);
	}

	private static int parseTabletteRow(String epiNum, String full) {
		return full.charAt(epiNum.length() + 1) - '1';
	}

	private static int parseTabletteCol(String epiNum, String full) {
		return full.charAt(epiNum.length()) - '1';
	}

	private static int inferBlocsPerTablette(List<Emplacement> cluster, String ep, int el, int cols, int rows) {
		if (cols <= 0 || rows <= 0) {
			return 0;
		}
		String tab0 = codeTablette(ep, 0, 0);
		return (int) cluster.stream()
			.filter(e -> e.getTypeEmp() == TypeEmp.BLOC && e.getNumero().startsWith(tab0) && e.getNumero().length() == el + 3)
			.count();
	}

	private static int inferBlocCm(List<Emplacement> cluster) {
		return cluster.stream()
			.filter(e -> e.getTypeEmp() == TypeEmp.BLOC)
			.findFirst()
			.map(e -> (int) Math.round(e.getMetrage() == null ? 0.0 : e.getMetrage()))
			.orElse(0);
	}

	private EpiMatrixDto buildMatrixDto(Emplacement epi) {
		String epiNum = safeEpi(epi.getNumero());
		List<Emplacement> cluster = loadCluster(epiNum);
		BoiteClusterContext boiteCtx = loadBoiteClusterContext(cluster);
		GridDims d = resolveDims(cluster, epiNum);

		List<TraversHeaderDto> headers = new ArrayList<>();
		for (int col = 0; col < d.cols(); col++) {
			headers.add(new TraversHeaderDto(col, codeTravers(epiNum, col)));
		}

		Map<String, Emplacement> key = new HashMap<>();
		for (Emplacement t : cluster) {
			if (t.getTypeEmp() == TypeEmp.TABLETTE && t.getNumero().length() == epiNum.length() + 2) {
				int c = parseTabletteCol(epiNum, t.getNumero());
				int r = parseTabletteRow(epiNum, t.getNumero());
				key.put(r + ":" + c, t);
			}
		}

		List<List<TabletteCellDto>> rows = new ArrayList<>();
		for (int r = 0; r < d.rows(); r++) {
			List<TabletteCellDto> line = new ArrayList<>();
			for (int c = 0; c < d.cols(); c++) {
				Emplacement tab = key.get(r + ":" + c);
				if (tab == null) {
					throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY", "Cellule manquante en base.");
				}
				line.add(toCellDto(tab, r, c, cluster, boiteCtx));
			}
			rows.add(line);
		}
		return new EpiMatrixDto(toSummary(epiNum, cluster, null), headers, rows);
	}

	/**
	 * Références {@code ID_BOITE} restées sur des blocs après suppression d’une boîte :
	 * remise à {@code null} pour que la matrice affiche « libre ».
	 */
	private void releaseOrphanedBoiteIds(List<Emplacement> cluster) {
		Set<Long> referenced = cluster.stream()
			.filter(e -> e.getTypeEmp() == TypeEmp.BLOC && e.getBoiteId() != null)
			.map(Emplacement::getBoiteId)
			.collect(Collectors.toCollection(HashSet::new));
		if (referenced.isEmpty()) {
			return;
		}
		Set<Long> existing = boiteRepository.findAllById(referenced).stream()
			.map(Boite::getId)
			.collect(Collectors.toCollection(HashSet::new));
		List<Emplacement> stale = cluster.stream()
			.filter(e -> e.getTypeEmp() == TypeEmp.BLOC && e.getBoiteId() != null && !existing.contains(e.getBoiteId()))
			.toList();
		if (stale.isEmpty()) {
			return;
		}
		for (Emplacement emp : stale) {
			emp.setBoiteId(null);
		}
		emplacementRepository.saveAll(stale);
	}

	private enum EmplacementDeletionScope {
		EPI,
		LAST_TRAVEE,
		LAST_TABLETTE_ROW,
		LAST_BLOC_SLOT
	}

	/** Refuse la suppression si des boîtes occupent encore des blocs ({@code EMPLACEMENTS.ID_BOITE}). */
	private void assertEmplacementsDeletable(
		List<Emplacement> emplacements,
		EmplacementDeletionScope scope,
		String epiNumero
	) {
		if (emplacements == null || emplacements.isEmpty()) {
			return;
		}
		String epiLabel = safeEpi(epiNumero);
		long occupiedBlocs = emplacements.stream()
			.filter(e -> e.getTypeEmp() == TypeEmp.BLOC && e.getBoiteId() != null)
			.count();
		if (occupiedBlocs > 0) {
			throw new ApiException(
				HttpStatus.BAD_REQUEST,
				"EMPLACEMENTS_OCCUPIED",
				occupiedBlocsMessage(scope, epiLabel, occupiedBlocs)
			);
		}
	}

	private static String occupiedBlocsMessage(EmplacementDeletionScope scope, String epiLabel, long count) {
		String suffix = count > 1 ? "s" : "";
		return switch (scope) {
			case EPI -> String.format(
				"Impossible de supprimer l'épi %s : cet épi contient encore %d bloc%s occupé%s par des boîtes. "
					+ "Libérez les emplacements, puis réessayez.",
				epiLabel, count, suffix, suffix);
			case LAST_TRAVEE -> String.format(
				"Impossible de supprimer la dernière travée de l'épi %s : cette travée contient encore %d bloc%s occupé%s. "
					+ "Libérez les emplacements concernés, puis réessayez.",
				epiLabel, count, suffix, suffix);
			case LAST_TABLETTE_ROW -> String.format(
				"Impossible de supprimer la dernière ligne de tablettes de l'épi %s : des blocs sont encore occupés (%d). "
					+ "Libérez les emplacements concernés, puis réessayez.",
				epiLabel, count);
			case LAST_BLOC_SLOT -> String.format(
				"Impossible de réduire le nombre de blocs par tablette de l'épi %s : %d bloc%s encore occupé%s sur la dernière colonne. "
					+ "Libérez ces emplacements, puis réessayez.",
				epiLabel, count, suffix, suffix);
		};
	}

	private BoiteClusterContext loadBoiteClusterContext(List<Emplacement> cluster) {
		List<Long> boiteIds = cluster.stream()
			.filter(e -> e.getTypeEmp() == TypeEmp.BLOC && e.getBoiteId() != null)
			.map(Emplacement::getBoiteId)
			.distinct()
			.toList();
		if (boiteIds.isEmpty()) {
			return BoiteClusterContext.empty();
		}
		Map<Long, Boite> map = new HashMap<>();
		for (Boite b : boiteRepository.findWithDocumentTypeByIdIn(boiteIds)) {
			map.put(b.getId(), b);
		}
		Map<Long, String> numerosAffiche = new HashMap<>();
		Map<Long, Integer> boiteCountByBordereau = new HashMap<>();
		try {
			for (BoiteBordereauNumeroProjection row : boiteRepository.findBordereauNumeroAfficheByBoiteIdIn(boiteIds)) {
				if (row.getBoiteId() != null) {
					numerosAffiche.put(row.getBoiteId(), row.getNumeroAffiche());
				}
				if (row.getBordereauId() != null) {
					Integer nb = row.getNombreBoites();
					boiteCountByBordereau.put(row.getBordereauId(), nb == null ? 0 : nb);
				}
			}
		} catch (RuntimeException ex) {
			log.warn("Projection bordereau (numéro affiché + nombre de boîtes) en échec — repli sur un calcul direct.", ex);
			numerosAffiche.clear();
			boiteCountByBordereau.clear();
			for (Boite box : map.values()) {
				if (box.getBordereau() != null) {
					if (box.getBordereau().getNumeroAffiche() != null) {
						numerosAffiche.put(box.getId(), box.getBordereau().getNumeroAffiche());
					}
					Long brId = box.getBordereau().getId();
					if (brId != null && !boiteCountByBordereau.containsKey(brId)) {
						boiteCountByBordereau.put(brId, (int) boiteRepository.countByBordereauId(brId));
					}
				}
			}
		}
		for (Long bordereauId : boiteCountByBordereau.keySet().stream().toList()) {
			Integer current = boiteCountByBordereau.get(bordereauId);
			if (current == null || current <= 0) {
				boiteCountByBordereau.put(bordereauId, (int) boiteRepository.countByBordereauId(bordereauId));
			}
		}
		Map<Long, Boolean> nonContiguesByBordereau = computeBordereauBoitesNonContigues(cluster, map, boiteCountByBordereau);
		return new BoiteClusterContext(map, numerosAffiche, boiteCountByBordereau, nonContiguesByBordereau);
	}

	/**
	 * Plusieurs boîtes d’un même bordereau séparées sur l’épi (pas un seul bloc contigu commun),
	 * même lorsque chaque boîte occupe plusieurs blocs consécutifs (ex. 01117–01118).
	 */
	private static Map<Long, Boolean> computeBordereauBoitesNonContigues(
		List<Emplacement> cluster,
		Map<Long, Boite> boitesById,
		Map<Long, Integer> boiteCountByBordereau
	) {
		Map<Long, Long> boiteToBordereau = new HashMap<>();
		for (Boite box : boitesById.values()) {
			if (box.getId() != null && box.getBordereau() != null && box.getBordereau().getId() != null) {
				boiteToBordereau.put(box.getId(), box.getBordereau().getId());
			}
		}
		List<Emplacement> sortedBlocs = cluster.stream()
			.filter(e -> e.getTypeEmp() == TypeEmp.BLOC)
			.sorted(Comparator.comparing(Emplacement::getNumero))
			.toList();
		Map<Long, Boolean> result = new HashMap<>();
		for (Map.Entry<Long, Integer> entry : boiteCountByBordereau.entrySet()) {
			Long bordereauId = entry.getKey();
			if (entry.getValue() == null || entry.getValue() < 2) {
				result.put(bordereauId, false);
				continue;
			}
			int runs = 0;
			boolean inRun = false;
			for (Emplacement emp : sortedBlocs) {
				Long boiteId = emp.getBoiteId();
				Long brId = boiteId == null ? null : boiteToBordereau.get(boiteId);
				boolean belongs = bordereauId.equals(brId);
				if (belongs) {
					if (!inRun) {
						runs++;
						inRun = true;
					}
				} else {
					inRun = false;
				}
			}
			result.put(bordereauId, runs > 1);
		}
		return result;
	}

	private TabletteCellDto toCellDto(Emplacement tablette, int r, int c, List<Emplacement> cluster,
			BoiteClusterContext boiteCtx) {
		String full = tablette.getNumero();
		String epiNum = full == null || full.length() < 2 ? "" : full.substring(0, full.length() - 2);
		epiNum = safeEpi(epiNum);
		Map<Long, Boite> boiteById = boiteCtx.boitesById() == null ? Map.of() : boiteCtx.boitesById();
		List<BlocDto> blocs = cluster.stream()
			.filter(e -> e.getTypeEmp() == TypeEmp.BLOC && e.getNumero() != null && e.getNumero().startsWith(full)
				&& e.getNumero().length() == full.length() + 1)
			.sorted(Comparator.comparing(Emplacement::getNumero))
			.map(bloc -> toBlocDto(bloc, bloc.getBoiteId() == null ? null : boiteById.get(bloc.getBoiteId()), boiteCtx))
			.toList();
		int occupied = (int) blocs.stream().filter(b -> b.boiteId() != null).count();
		String num = codeTablette(epiNum, parseTabletteCol(epiNum, full), parseTabletteRow(epiNum, full));
		return new TabletteCellDto(tablette.getId(), r, c, num, blocs, occupied, blocs.size());
	}

	private BlocDto toBlocDto(Emplacement bloc, Boite boite, BoiteClusterContext boiteCtx) {
		String num = bloc.getNumero();
		int slot = num == null || num.isEmpty() ? 0 : num.charAt(num.length() - 1) - '1';
		Long boiteId = bloc.getBoiteId();
		if (boiteId == null || boite == null) {
			return new BlocDto(bloc.getId(), slot, num, null, null);
		}
		String typeTitle = boite.getDocumentType() != null ? boite.getDocumentType().getTitle() : null;
		Map<Long, String> numerosAfficheMap = boiteCtx.bordereauNumeroAfficheByBoiteId();
		String numeroBordereau = numerosAfficheMap != null ? numerosAfficheMap.get(boite.getId()) : null;
		if (numeroBordereau == null || numeroBordereau.isBlank()) {
			Bordereau br = boite.getBordereau();
			numeroBordereau = br != null ? br.getNumeroAffiche() : null;
		}
		Long bordereauId = boite.getBordereau() != null ? boite.getBordereau().getId() : null;
		Map<Long, Integer> countsMap = boiteCtx.bordereauBoiteCountByBordereauId();
		int bordereauBoiteCount = 1;
		if (bordereauId != null && countsMap != null) {
			Integer fromMap = countsMap.get(bordereauId);
			bordereauBoiteCount = fromMap == null ? 1 : fromMap;
		}
		Map<Long, Boolean> nonContiguesMap = boiteCtx.bordereauBoitesNonContiguesByBordereauId();
		boolean bordereauNonContigu = false;
		if (bordereauId != null && nonContiguesMap != null) {
			Boolean fromMap = nonContiguesMap.get(bordereauId);
			bordereauNonContigu = fromMap != null && fromMap;
		}
		BlocBoiteSummaryDto summary = new BlocBoiteSummaryDto(
			boite.getId(),
			boite.getTitre(),
			boite.getAnneeMin(),
			boite.getAnneeMax(),
			boite.getMetrageCm(),
			typeTitle,
			boite.getMotsCles() == null ? "" : boite.getMotsCles(),
			numeroBordereau,
			bordereauId,
			bordereauBoiteCount,
			bordereauNonContigu);
		return new BlocDto(bloc.getId(), slot, num, boite.getId(), summary);
	}

	private static String safeEpi(String epiNumero) {
		return epiNumero == null ? "" : epiNumero.trim();
	}

	private static String codeTravers(String epiNumero, int trav0based) {
		return safeEpi(epiNumero) + (trav0based + 1);
	}

	private static String codeTablette(String epiNumero, int trav0based, int line0based) {
		return safeEpi(epiNumero) + (trav0based + 1) + (line0based + 1);
	}

	private static String codeBloc(String epiNumero, int trav0based, int line0based, int slot0based) {
		return safeEpi(epiNumero) + (trav0based + 1) + (line0based + 1) + (slot0based + 1);
	}

	private EpiSummaryDto toSummary(String epiNum, List<Emplacement> cluster, String typeLabelOverride) {
		Emplacement epiRow = cluster.stream()
			.filter(e -> e.getTypeEmp() == TypeEmp.EPI && safeEpi(e.getNumero()).equals(safeEpi(epiNum)))
			.findFirst()
			.orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY", "Ligne épi manquante."));
		GridDims d = resolveDims(cluster, epiNum);
		int totalCm = (int) Math.round(epiRow.getMetrage() == null ? 0.0 : epiRow.getMetrage());
		String type = typeLabelOverride != null && !typeLabelOverride.isBlank() ? typeLabelOverride : "Épi";
		return new EpiSummaryDto(
			epiRow.getId(),
			epiRow.getNumero(),
			type,
			d.cols(),
			d.rows(),
			d.bp(),
			d.cm(),
			totalCm);
	}

	/** Tablettes dont le taux d'occupation (blocs occupés / total) ≥ {@code seuilPercent}. */
	@Transactional(readOnly = true)
	public TabletteFillAlertPageResponse listLignesPresquePleines(
		Authentication authentication,
		int seuilPercent,
		Pageable pageable
	) {
		authorization.requireAuthenticated(authentication);
		int seuil = seuilPercent > 0 ? seuilPercent : 75;
		Map<String, String> epiIdByNumero = emplacementRepository.findByTypeEmp(TypeEmp.EPI).stream()
			.collect(Collectors.toMap(e -> safeEpi(e.getNumero()), Emplacement::getId, (a, b) -> a));

		Page<Object[]> page = emplacementRepository.findTablettesPresquePleinesPage(
			seuil,
			PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())
		);
		List<TabletteFillAlertDto> content = page.getContent().stream()
			.map(row -> toTabletteFillAlert(row, epiIdByNumero))
			.toList();
		return new TabletteFillAlertPageResponse(
			content,
			page.getTotalElements(),
			page.getTotalPages(),
			page.getNumber(),
			page.getSize()
		);
	}

	private TabletteFillAlertDto toTabletteFillAlert(Object[] row, Map<String, String> epiIdByNumero) {
		String tabletteId = row[0] == null ? "" : row[0].toString();
		String tabNum = row[1] == null ? "" : row[1].toString();
		int linearCm = row[2] == null ? 0 : (int) Math.round(((Number) row[2]).doubleValue());
		int total = row[3] == null ? 0 : ((Number) row[3]).intValue();
		int occupied = row[4] == null ? 0 : ((Number) row[4]).intValue();
		int pct = row[5] == null ? 0 : ((Number) row[5]).intValue();
		String epiNum = tabNum.length() >= 2 ? safeEpi(tabNum.substring(0, tabNum.length() - 2)) : tabNum;
		int rowIdx = tabNum.length() >= 2 ? tabNum.charAt(tabNum.length() - 1) - '1' : 0;
		String rowLabel = String.format("Ligne %02d", Math.max(0, rowIdx) + 1);
		return new TabletteFillAlertDto(
			tabletteId,
			epiIdByNumero.get(epiNum),
			epiNum,
			tabNum,
			rowLabel,
			linearCm,
			occupied,
			total,
			pct
		);
	}

	@Transactional(readOnly = true)
	public long countLignesPresquePleines(Authentication authentication) {
		if (!authorization.isAdmin(authentication)) {
			return 0L;
		}
		return emplacementRepository.countTablettesPresquePleines(75);
	}

	private static String trimToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
