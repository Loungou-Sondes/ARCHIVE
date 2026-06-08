package ommp.archives.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ommp.archives.dto.boite.CreateDossierRequest;
import ommp.archives.dto.boite.DossierResponse;
import ommp.archives.entity.Boite;
import ommp.archives.entity.Dossier;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.BoiteRepository;
import ommp.archives.repository.DossierRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class DossierService {

	private final BoiteRepository boiteRepository;
	private final DossierRepository dossierRepository;
	private final AuthorizationService authorization;

	public DossierService(
		BoiteRepository boiteRepository,
		DossierRepository dossierRepository,
		AuthorizationService authorization
	) {
		this.boiteRepository = boiteRepository;
		this.dossierRepository = dossierRepository;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public List<DossierResponse> listByBoite(Authentication authentication, Long boiteId) {
		Boite box = loadBoiteWithAccess(authentication, boiteId);
		return dossierRepository.findByBoite_IdOrderByAnneeDescTitreAsc(box.getId()).stream()
			.map(this::toResponse)
			.toList();
	}

	@Transactional
	public DossierResponse create(
		Authentication authentication,
		Long boiteId,
		CreateDossierRequest request
	) {
		Boite box = loadBoiteWithAccess(authentication, boiteId);
		String titre = request.titre().trim();
		if (titre.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", "Le titre du dossier est obligatoire.");
		}
		assertAnneeDansPlageBoite(box, request.annee());
		Dossier dossier = new Dossier();
		dossier.setBoite(box);
		dossier.setTitre(titre);
		dossier.setContenu(normalizeContenu(request.contenu()));
		dossier.setAnnee(request.annee());
		return toResponse(dossierRepository.save(dossier));
	}

	@Transactional
	public void delete(Authentication authentication, Long boiteId, Long dossierId) {
		loadBoiteWithAccess(authentication, boiteId);
		Dossier dossier = dossierRepository.findByIdAndBoite_Id(dossierId, boiteId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Dossier introuvable."));
		dossierRepository.delete(dossier);
	}

	private Boite loadBoiteWithAccess(Authentication authentication, Long boiteId) {
		authorization.requireAuthenticated(authentication);
		Boite box = boiteRepository.findById(boiteId)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Boîte introuvable."));
		authorization.assertCanReadBoite(authentication, box);
		return box;
	}

	private static void assertAnneeDansPlageBoite(Boite box, int annee) {
		int min = box.getAnneeMin();
		int max = box.getAnneeMax();
		if (annee < min || annee > max) {
			String message = min == max
				? "L'année du dossier doit être " + min + "."
				: "L'année du dossier doit être comprise entre " + min + " et " + max + ".";
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION", message);
		}
	}

	private static String normalizeContenu(String contenu) {
		if (contenu == null) {
			return null;
		}
		String t = contenu.trim();
		return t.isEmpty() ? null : t;
	}

	private DossierResponse toResponse(Dossier d) {
		return new DossierResponse(
			d.getId(),
			d.getBoite().getId(),
			d.getTitre(),
			d.getContenu(),
			d.getAnnee(),
			d.getCreatedAt() == null ? null : d.getCreatedAt().toString()
		);
	}
}
