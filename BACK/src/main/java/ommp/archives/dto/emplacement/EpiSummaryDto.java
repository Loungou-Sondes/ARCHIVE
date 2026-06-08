package ommp.archives.dto.emplacement;

public record EpiSummaryDto(
	String id,
	String numero,
	String type,
	int traversCount,
	int tabletteRows,
	int blocsPerTablette,
	int blocLinearCm,
	int totalLinearCm
) {
}
