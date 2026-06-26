package ommp.archives.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import ommp.archives.dto.boite.ArchivesIntermediairesGroupedPageResponse;
import ommp.archives.dto.boite.BoiteAlerteArchivesRow;
import ommp.archives.dto.boite.BoiteConsultationDto;
import ommp.archives.dto.boite.CreateDossierRequest;
import ommp.archives.dto.boite.DossierResponse;
import ommp.archives.dto.boite.BoiteEcheanceAlerteResponse;
import ommp.archives.dto.boite.BoiteSemiActifAlerteResponse;
import ommp.archives.dto.boite.ReporterSemiActifAlerteRequest;
import ommp.archives.dto.boite.BoiteRechercheItemDto;
import ommp.archives.dto.boite.SemanticSearchRequest;
import ommp.archives.dto.boite.SemanticSearchResponse;
import ommp.archives.dto.boite.HistoriqueGroupedPageResponse;
import ommp.archives.service.BoiteAlerteService;
import ommp.archives.service.BoiteArchivesIntermediairesService;
import ommp.archives.service.BoiteHistoriqueService;
import ommp.archives.service.BoiteRechercheService;
import ommp.archives.service.BoiteSemanticSearchService;
import ommp.archives.service.DossierService;

import java.util.List;

@RestController
@RequestMapping("/api/boites")
public class BoiteController {

	private final BoiteAlerteService boiteAlerteService;
	private final BoiteHistoriqueService boiteHistoriqueService;
	private final BoiteArchivesIntermediairesService boiteArchivesIntermediairesService;
	private final BoiteRechercheService boiteRechercheService;
	private final BoiteSemanticSearchService boiteSemanticSearchService;
	private final DossierService dossierService;

	public BoiteController(
		BoiteAlerteService boiteAlerteService,
		BoiteHistoriqueService boiteHistoriqueService,
		BoiteArchivesIntermediairesService boiteArchivesIntermediairesService,
		BoiteRechercheService boiteRechercheService,
		BoiteSemanticSearchService boiteSemanticSearchService,
		DossierService dossierService
	) {
		this.boiteAlerteService = boiteAlerteService;
		this.boiteHistoriqueService = boiteHistoriqueService;
		this.boiteArchivesIntermediairesService = boiteArchivesIntermediairesService;
		this.boiteRechercheService = boiteRechercheService;
		this.boiteSemanticSearchService = boiteSemanticSearchService;
		this.dossierService = dossierService;
	}

	@PostMapping("/recherche-semantique")
	public ResponseEntity<SemanticSearchResponse> rechercheSemantique(
		Authentication authentication,
		@Valid @RequestBody SemanticSearchRequest request
	) {
		return ResponseEntity.ok(boiteSemanticSearchService.search(authentication, request));
	}

	@GetMapping("/recherche")
	public ResponseEntity<Page<BoiteRechercheItemDto>> rechercheBoites(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) String nom,
		@RequestParam(required = false) String motsCles,
		@RequestParam(required = false) Integer anneeMin,
		@RequestParam(required = false) Integer anneeMax,
		@PageableDefault(size = 20, sort = "titre") Pageable pageable
	) {
		return ResponseEntity.ok(
			boiteRechercheService.search(authentication, q, nom, motsCles, anneeMin, anneeMax, pageable)
		);
	}

	@GetMapping("/alertes-archives")
	public ResponseEntity<Page<BoiteAlerteArchivesRow>> listAlertesArchives(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@PageableDefault(size = 12, sort = "titre") Pageable pageable
	) {
		return ResponseEntity.ok(boiteAlerteService.listAlertesArchives(authentication, q, pageable));
	}

	@PostMapping("/{id}/reporter-alerte-semi-actif")
	public ResponseEntity<BoiteSemiActifAlerteResponse> reporterSemiActifAlerte(
		Authentication authentication,
		@PathVariable Long id,
		@Valid @RequestBody ReporterSemiActifAlerteRequest request
	) {
		return ResponseEntity.ok(boiteAlerteService.reporterSemiActifAlerte(authentication, id, request.annee()));
	}

	@PostMapping("/{id}/approuver-destruction-transfert")
	public ResponseEntity<BoiteEcheanceAlerteResponse> approuverDestructionTransfert(
		Authentication authentication,
		@PathVariable Long id
	) {
		return ResponseEntity.ok(boiteAlerteService.approuverDestructionTransfert(authentication, id));
	}

	@GetMapping("/archives-intermediaires")
	public ResponseEntity<ArchivesIntermediairesGroupedPageResponse> listArchivesIntermediaires(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@PageableDefault(size = 5) Pageable pageable
	) {
		return ResponseEntity.ok(boiteArchivesIntermediairesService.listGrouped(authentication, q, pageable));
	}

	@GetMapping("/historique")
	public ResponseEntity<HistoriqueGroupedPageResponse> listHistorique(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) String typeEtat,
		@PageableDefault(size = 5) Pageable pageable
	) {
		return ResponseEntity.ok(boiteHistoriqueService.listGrouped(authentication, q, typeEtat, pageable));
	}

	@GetMapping("/{id}")
	public ResponseEntity<BoiteConsultationDto> getBoite(
		Authentication authentication,
		@PathVariable Long id
	) {
		return ResponseEntity.ok(boiteRechercheService.getById(authentication, id));
	}

	@GetMapping("/{boiteId}/dossiers")
	public ResponseEntity<List<DossierResponse>> listDossiers(
		Authentication authentication,
		@PathVariable Long boiteId
	) {
		return ResponseEntity.ok(dossierService.listByBoite(authentication, boiteId));
	}

	@PostMapping("/{boiteId}/dossiers")
	public ResponseEntity<DossierResponse> createDossier(
		Authentication authentication,
		@PathVariable Long boiteId,
		@Valid @RequestBody CreateDossierRequest request
	) {
		return ResponseEntity.ok(dossierService.create(authentication, boiteId, request));
	}

	@DeleteMapping("/{boiteId}/dossiers/{dossierId}")
	public ResponseEntity<Void> deleteDossier(
		Authentication authentication,
		@PathVariable Long boiteId,
		@PathVariable Long dossierId
	) {
		dossierService.delete(authentication, boiteId, dossierId);
		return ResponseEntity.noContent().build();
	}
}
