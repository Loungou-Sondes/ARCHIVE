package ommp.archives.dto.dashboard;

/**
 * Période optionnelle pour les indicateurs « sur la période » et le graphique d'activité.
 */
public enum DashboardPeriod {
	CURRENT,
	DAYS_30,
	YTD;

	public static DashboardPeriod fromParam(String raw) {
		if (raw == null || raw.isBlank()) {
			return CURRENT;
		}
		return switch (raw.trim().toLowerCase()) {
			case "30d", "days_30" -> DAYS_30;
			case "ytd" -> YTD;
			default -> CURRENT;
		};
	}
}
