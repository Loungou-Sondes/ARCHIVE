package ommp.archives.service;

import java.time.LocalDate;
import java.util.Optional;

import ommp.archives.entity.Boite;
import ommp.archives.entity.ConservationRule;
import ommp.archives.entity.FinalDecision;

/**
 * Année de destruction ou de transfert : {@code anneeMax + années semi-actives}
 * (les années actives ne prolongent pas cette année d’action).
 */
public final class ConservationDueDateCalculator {

	private ConservationDueDateCalculator() {
	}

	public static Optional<Integer> computeEcheanceYear(Boite box, ConservationRule rule) {
		if (box == null || rule == null) {
			return Optional.empty();
		}
		FinalDecision fd = rule.getFinalDecision();
		if (fd != FinalDecision.DETRUIRE && fd != FinalDecision.TRANSFERER) {
			return Optional.empty();
		}
		if (rule.isSemiActiveUnknown()) {
			return Optional.empty();
		}
		Integer semiActiveYears = rule.getSemiActiveYears();
		if (semiActiveYears == null || semiActiveYears < 0) {
			return Optional.empty();
		}
		int dueYear = box.getAnneeMax() + semiActiveYears;
		if (dueYear < 1900 || dueYear > 2200) {
			return Optional.empty();
		}
		return Optional.of(dueYear);
	}

	/**
	 * Alerte si l’année d’action est antérieure ou égale à l’année civile courante.
	 */
	public static boolean isDueForDestructionTransfertAlert(Boite box, ConservationRule rule, LocalDate today) {
		if (today == null) {
			return false;
		}
		return computeEcheanceYear(box, rule)
			.map(year -> year <= today.getYear())
			.orElse(false);
	}
}
