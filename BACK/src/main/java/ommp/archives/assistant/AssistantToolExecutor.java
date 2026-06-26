package ommp.archives.assistant;

import java.util.ArrayList;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import ommp.archives.dto.alertes.AlertesCountResponse;
import ommp.archives.dto.dashboard.DashboardKpiDto;
import ommp.archives.dto.dashboard.DashboardPeriod;
import ommp.archives.dto.dashboard.DashboardStatsResponse;
import ommp.archives.service.AlertesCountService;
import ommp.archives.service.DashboardService;

@Component
public class AssistantToolExecutor {

	private final AlertesCountService alertesCountService;
	private final DashboardService dashboardService;
	private final AssistantFaqKnowledge faqKnowledge;

	public AssistantToolExecutor(
		AlertesCountService alertesCountService,
		DashboardService dashboardService,
		AssistantFaqKnowledge faqKnowledge
	) {
		this.alertesCountService = alertesCountService;
		this.dashboardService = dashboardService;
		this.faqKnowledge = faqKnowledge;
	}

	public String getApplicationStats() {
		Authentication authentication = AssistantRequestContext.authentication();
		if (authentication == null) {
			return "Session non disponible.";
		}
		return formatAdminStats(authentication);
	}

	public String getAlertesSummary() {
		Authentication authentication = AssistantRequestContext.authentication();
		if (authentication == null) {
			return "Session non disponible.";
		}
		AlertesCountResponse counts = alertesCountService.getCounts(authentication);
		if (counts.total() == 0) {
			return "Aucune alerte en cours.";
		}
		List<String> lines = new ArrayList<>();
		lines.add("Alertes en cours (" + counts.total() + " au total) :");
		appendCount(lines, counts.bordereauxEnAttente(), "bordereau(x) en attente d'affectation");
		appendCount(lines, counts.bordereauxValidationAgents(), "bordereau(x) agents à valider");
		appendCount(lines, counts.boitesSemiActif(), "boîte(s) semi-actif inconnue");
		appendCount(lines, counts.boitesEcheance(), "boîte(s) à échéance destruction/transfert");
		appendCount(lines, counts.lignesPleines(), "ligne(s) presque pleine(s)");
		return String.join("\n", lines);
	}

	public String getApplicationHelp(String topic) {
		String normalized = topic != null ? topic.trim().toLowerCase() : "general";
		if (normalized.isBlank()) {
			normalized = "general";
		}
		return faqKnowledge.answer(normalized);
	}

	private String formatAdminStats(Authentication authentication) {
		AlertesCountResponse alertes = alertesCountService.getCounts(authentication);
		DashboardStatsResponse dashboard = dashboardService.getStats(authentication, DashboardPeriod.CURRENT);
		DashboardKpiDto kpi = dashboard.kpis();

		List<String> lines = new ArrayList<>();
		lines.add("Données actuelles (base OMMP) :");
		lines.add("");
		lines.add("Bordereaux :");
		lines.add("• " + kpi.bordereauxAffectes() + " affecté(s)");
		lines.add("• " + kpi.bordereauxEnAttente() + " en attente");
		lines.add("");
		lines.add("Boîtes par état :");
		lines.add("• " + kpi.boitesSemiActif() + " semi-actif");
		lines.add("• " + kpi.boitesTransfert() + " en transfert");
		lines.add("• " + kpi.boitesDestruction() + " en destruction");
		lines.add("");
		lines.add("Stockage :");
		lines.add("• " + kpi.epis() + " épi(s)");
		lines.add("• " + kpi.blocsOccupes() + " / " + kpi.blocsTotal() + " blocs occupés (" + kpi.occupationPourcent() + " %)");
		lines.add("• " + kpi.agents() + " agent(s) enregistré(s)");
		lines.add("");
		lines.add("Alertes (" + alertes.total() + " au total) :");
		appendCount(lines, alertes.bordereauxEnAttente(), "bordereau(x) en attente d'affectation");
		appendCount(lines, alertes.bordereauxValidationAgents(), "bordereau(x) agents à valider");
		appendCount(lines, alertes.boitesSemiActif(), "boîte(s) semi-actif inconnue");
		appendCount(lines, alertes.boitesEcheance(), "boîte(s) à échéance destruction/transfert");
		appendCount(lines, alertes.lignesPleines(), "ligne(s) presque pleine(s)");
		if (alertes.total() == 0) {
			lines.add("• aucune alerte en cours");
		}
		return String.join("\n", lines);
	}

	private void appendCount(List<String> lines, long count, String label) {
		if (count > 0) {
			lines.add("• " + count + " " + label);
		}
	}
}
