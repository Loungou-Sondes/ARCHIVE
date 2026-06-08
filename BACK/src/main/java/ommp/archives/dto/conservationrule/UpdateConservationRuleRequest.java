package ommp.archives.dto.conservationrule;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import ommp.archives.entity.FinalDecision;

public record UpdateConservationRuleRequest(
	@NotBlank String reference,
	@NotNull Long documentTypeId,
	@NotNull FinalDecision finalDecision,
	Boolean activeUnknown,
	Integer activeYears,
	Boolean semiActiveUnknown,
	Integer semiActiveYears
) {
}
