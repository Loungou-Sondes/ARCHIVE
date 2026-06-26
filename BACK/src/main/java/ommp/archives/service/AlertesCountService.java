package ommp.archives.service;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ommp.archives.dto.alertes.AlertesCountResponse;
import ommp.archives.security.AuthorizationService;

@Service
public class AlertesCountService {

	private final BordereauService bordereauService;
	private final BoiteAlerteService boiteAlerteService;
	private final EmplacementService emplacementService;
	private final AuthorizationService authorization;

	public AlertesCountService(
		BordereauService bordereauService,
		BoiteAlerteService boiteAlerteService,
		EmplacementService emplacementService,
		AuthorizationService authorization
	) {
		this.bordereauService = bordereauService;
		this.boiteAlerteService = boiteAlerteService;
		this.emplacementService = emplacementService;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public AlertesCountResponse getCounts(Authentication authentication) {
		long bordereauxEnAttente = authorization.isAdmin(authentication)
			? bordereauService.countEnAttente(authentication)
			: 0L;
		long bordereauxValidationAgents = authorization.isAdmin(authentication)
			? bordereauService.countValidationAgents(authentication)
			: 0L;
		long boitesSemiActif = boiteAlerteService.countSemiActifInconnue(authentication);
		long boitesEcheance = boiteAlerteService.countEcheanceDestructionTransfert(authentication);
		long lignesPleines = emplacementService.countLignesPresquePleines(authentication);
		long total =
			bordereauxEnAttente
				+ bordereauxValidationAgents
				+ boitesSemiActif
				+ boitesEcheance
				+ lignesPleines;
		return new AlertesCountResponse(
			total,
			bordereauxEnAttente,
			bordereauxValidationAgents,
			boitesSemiActif,
			boitesEcheance,
			lignesPleines,
			0L
		);
	}
}
