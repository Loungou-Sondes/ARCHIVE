package ommp.archives.dto.emplacement;

/**
 * Projection pour le n° affiché du bordereau ({@link ommp.archives.entity.Bordereau#getNumeroAffiche()}) par id de boîte.
 */
public interface BoiteBordereauNumeroProjection {

	Long getBoiteId();

	Long getBordereauId();

	String getNumeroAffiche();

	/**
	 * {@link ommp.archives.entity.Bordereau#getNombreBoites()}.
	 * {@code Integer} (et non {@code int}) car la colonne {@code NOMBRE_BOITES} peut être {@code NULL}
	 * sur les lignes existantes tant que le script de migration n’a pas été exécuté.
	 */
	Integer getNombreBoites();
}
