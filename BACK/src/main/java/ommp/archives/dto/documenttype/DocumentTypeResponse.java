package ommp.archives.dto.documenttype;

public record DocumentTypeResponse(
	Long id,
	String title,
	String directionId,
	String directionLabel
) {
}
