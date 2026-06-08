package ommp.archives.dto.dashboard;

/**
 * Cartes KPI du tableau de bord (vue globale administrateur).
 */
public record DashboardKpiDto(
	long bordereauxAffectes,
	long bordereauxEnAttente,
	/** Bordereaux dont {@code DATE_TRANSFERT} tombe dans la période choisie ; {@code null} si période « état actuel ». */
	Long bordereauxSurPeriode,
	long boitesSemiActif,
	long boitesTransfert,
	long boitesDestruction,
	long epis,
	long blocsTotal,
	long blocsOccupes,
	int occupationPourcent,
	long agents
) {
}
