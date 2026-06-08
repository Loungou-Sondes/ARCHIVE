package ommp.archives.service;

import java.util.Comparator;
import java.util.UUID;

import ommp.archives.entity.Bordereau;
import ommp.archives.entity.BordereauStatut;

/**
 * Numérotation : les bordereaux {@code EN_ATTENTE} n'ont pas de numéro officiel
 * ({@code rang-année}) ; il est attribué à la validation ({@code AFFECTE}).
 */
public final class BordereauNumeroSupport {

	public static final String PENDING_PREFIX = "ATT-";
	/** Préfixe avant insert (UK) ; remplacé par {@link #applyProvisionalNumero} après obtention de l’id. */
	private static final String PENDING_PLACEHOLDER_PREFIX = "ATT-PENDING-";
	/** {@code BORDEREAUX.NUMERO_AFFICHE} = VARCHAR2(32). */
	public static final int NUMERO_AFFICHE_MAX_LENGTH = 32;

	private BordereauNumeroSupport() {
	}

	/**
	 * Placeholder unique ≤ 32 caractères pour la première persistance d’un bordereau EN_ATTENTE.
	 */
	public static String temporaryPlaceholder() {
		int suffixLen = NUMERO_AFFICHE_MAX_LENGTH - PENDING_PLACEHOLDER_PREFIX.length();
		String compact = UUID.randomUUID().toString().replace("-", "");
		return PENDING_PLACEHOLDER_PREFIX + compact.substring(0, suffixLen);
	}

	public static void applyProvisionalNumero(Bordereau b) {
		if (b == null || b.getId() == null) {
			return;
		}
		b.setRangNumero(0);
		b.setNumeroAffiche(PENDING_PREFIX + b.getId());
	}

	public static boolean isProvisional(String numeroAffiche) {
		return numeroAffiche != null && numeroAffiche.startsWith(PENDING_PREFIX);
	}

	public static boolean hasOfficialNumero(Bordereau b) {
		return b != null && b.getStatut() == BordereauStatut.AFFECTE && !isProvisional(b.getNumeroAffiche());
	}

	/** Libellé affiché côté UI (listes, détail). Les brouillons n’affichent pas de numéro officiel. */
	public static String displayNumero(Bordereau b) {
		if (b == null) {
			return "—";
		}
		if (b.getStatut() == BordereauStatut.EN_ATTENTE || isProvisional(b.getNumeroAffiche())) {
			return "En attente";
		}
		return b.getNumeroAffiche();
	}

	public static String formatOfficialNumero(int rang, int annee) {
		return rang + "-" + annee;
	}

	public static void assignOfficialNumero(Bordereau b, int nextRang, int annee) {
		b.setRangNumero(nextRang);
		b.setAnneeNumero(annee);
		b.setNumeroAffiche(formatOfficialNumero(nextRang, annee));
	}

	/** Tri numérique {@code rang-annee} (ex. 12-2026 avant 5-2026, 2026 avant 2025). */
	public static int compareOfficialNumero(Bordereau a, Bordereau b) {
		if (a == null && b == null) {
			return 0;
		}
		if (a == null) {
			return 1;
		}
		if (b == null) {
			return -1;
		}
		boolean aOfficial = hasOfficialNumero(a);
		boolean bOfficial = hasOfficialNumero(b);
		if (!aOfficial && !bOfficial) {
			return Long.compare(a.getId(), b.getId());
		}
		if (!aOfficial) {
			return 1;
		}
		if (!bOfficial) {
			return -1;
		}
		int byYear = Integer.compare(a.getAnneeNumero(), b.getAnneeNumero());
		if (byYear != 0) {
			return byYear;
		}
		return Integer.compare(a.getRangNumero(), b.getRangNumero());
	}

	/** Plus récent en premier (année puis rang décroissants). */
	public static Comparator<Bordereau> officialNumeroDescending() {
		return (a, b) -> -compareOfficialNumero(a, b);
	}

	public static Comparator<Bordereau> officialNumeroAscending() {
		return BordereauNumeroSupport::compareOfficialNumero;
	}

}
