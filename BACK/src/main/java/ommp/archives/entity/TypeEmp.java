package ommp.archives.entity;

/**
 * Valeurs de {@code EMPLACEMENTS.TYPE_EMP} — niveau dans la grille d'archives.
 */
public enum TypeEmp {

	/** Racine épi. */
	EPI,
	/** Colonne travée. */
	TRAVEE,
	/** Cellule tablette. */
	TABLETTE,
	/** Bloc occupable ({@link Emplacement#getBoiteId()}). */
	BLOC
}
