package ommp.archives.dto.dashboard;

import java.util.List;

public record AgentDashboardStatsResponse(
	long bordereauxAffectes,
	long bordereauxEnAttente,
	long totalBoites,
	List<DashboardChartSliceDto> bordereauxParStatut,
	List<DashboardChartSliceDto> boitesParEtat,
	List<DashboardMonthCountDto> activiteBordereaux,
	String directionLabel
) {
}
