package ommp.archives.dto.conservationrule;

import jakarta.validation.constraints.NotNull;

import ommp.archives.entity.ConservationRuleStatus;

public record UpdateConservationRuleStatusRequest(@NotNull ConservationRuleStatus status) {
}
