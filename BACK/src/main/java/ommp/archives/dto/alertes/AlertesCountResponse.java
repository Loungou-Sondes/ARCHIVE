package ommp.archives.dto.alertes;

/** Totaux affichés sur la cloche et le menu « Alertes et échéances ». */
public record AlertesCountResponse(
	long total,
	long bordereauxEnAttente,
	long bordereauxValidationAgents,
	long boitesSemiActif,
	long boitesEcheance,
	long lignesPleines,
	long reglesConservation,
	long passwordResetRequests
) {
}
