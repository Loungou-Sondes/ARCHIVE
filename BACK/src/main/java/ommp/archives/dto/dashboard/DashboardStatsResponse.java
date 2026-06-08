package ommp.archives.dto.dashboard;

import java.util.List;

public record DashboardStatsResponse(
	String period,
	DashboardKpiDto kpis,
	List<DashboardChartSliceDto> boitesParEtat,
	List<DashboardMonthCountDto> activiteBordereaux,
	List<DashboardAgentRankDto> agentsParVolume
) {
}
