package ommp.archives.dto.emplacement;

import java.util.List;

public record TabletteCellDto(
	String tabletteId,
	int rowIndex,
	int colIndex,
	String tabletteNumero,
	List<BlocDto> blocs,
	int occupiedCount,
	int totalCount
) {
}
