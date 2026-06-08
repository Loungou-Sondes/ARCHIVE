package ommp.archives.dto.dashboard;

public record DashboardMonthCountDto(
	int year,
	int month,
	String label,
	long count
) {
}
