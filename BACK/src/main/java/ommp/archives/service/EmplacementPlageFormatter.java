package ommp.archives.service;

import java.util.List;

import ommp.archives.entity.BoiteEtat;
import ommp.archives.entity.Emplacement;

/**
 * Affichage des plages d'emplacement (ex. métrage 10 cm → {@code 01112} au lieu de {@code 01112-01112}).
 */
public final class EmplacementPlageFormatter {

	private EmplacementPlageFormatter() {
	}

	public static String formatRange(String debut, String fin) {
		String a = trimOrNull(debut);
		String b = trimOrNull(fin);
		if (a == null && b == null) {
			return null;
		}
		if (a == null) {
			return b;
		}
		if (b == null || a.equals(b)) {
			return a;
		}
		return a + "-" + b;
	}

	public static String formatPlage(String plage) {
		String t = trimOrNull(plage);
		if (t == null) {
			return null;
		}
		int dash = t.indexOf('-');
		if (dash <= 0 || dash >= t.length() - 1) {
			return t;
		}
		String left = t.substring(0, dash).trim();
		String right = t.substring(dash + 1).trim();
		return left.equals(right) ? left : t;
	}

	/** Plage affichable à partir des blocs liés ({@code ID_BOITE}) ou copie historique dans {@link BoiteEtat}. */
	public static String resolveDisplayFromBlocs(List<Emplacement> blocs, BoiteEtat etat) {
		if (blocs != null && !blocs.isEmpty()) {
			String plage = formatRange(blocs.get(0).getNumero(), blocs.get(blocs.size() - 1).getNumero());
			if (plage != null) {
				return formatPlage(plage);
			}
		}
		if (etat != null && etat.getPositionId() != null && !etat.getPositionId().isBlank()) {
			return formatPlage(etat.getPositionId());
		}
		return null;
	}

	private static String trimOrNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.trim();
	}
}
