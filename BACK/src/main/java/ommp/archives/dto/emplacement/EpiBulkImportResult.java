package ommp.archives.dto.emplacement;

import java.util.List;

public record EpiBulkImportResult(
	int createdCount,
	List<String> numeros
) {
}
