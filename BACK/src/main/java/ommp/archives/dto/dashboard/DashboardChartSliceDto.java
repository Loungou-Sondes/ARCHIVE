package ommp.archives.dto.dashboard;

public record DashboardChartSliceDto(
	String label,
	String code,
	long value
) {
}
