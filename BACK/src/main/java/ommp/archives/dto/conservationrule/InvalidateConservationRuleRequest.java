package ommp.archives.dto.conservationrule;

import jakarta.validation.constraints.NotNull;

/** {@link InvalidationBoiteStrategy#KEEP_ON_OLD_RULE} : invalide la règle et fige les boîtes sur celle-ci. */
public record InvalidateConservationRuleRequest(@NotNull InvalidationBoiteStrategy boiteStrategy) {
}
