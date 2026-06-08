package ommp.archives.dto.emplacement;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ResizeEpiRequest(
	@NotNull @Min(1) @Max(9) Integer traversCount,
	@NotNull @Min(1) @Max(9) Integer tabletteRows,
	@NotNull @Min(1) @Max(8) Integer blocsPerTablette
) {
}
