package ommp.archives.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateAgentActiveRequest(@NotNull Boolean active) {
}
