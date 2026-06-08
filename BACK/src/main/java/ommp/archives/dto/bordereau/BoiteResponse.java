package ommp.archives.dto.bordereau;

public record BoiteResponse(
	Long id,
	String titre,
	int anneeMin,
	int anneeMax,
	int metrageCm,
	String contenu,
	String motsCles,
	Long documentTypeId,
	String documentTypeTitle,
	String emplacementBlocDebutId,
	String emplacementBlocFinId,
	String emplacementBlocPlage,
	/** Égal au début (compatibilité JSON). */
	String emplacementBlocId,
	/** Tous les numéros séparés par une virgule (même valeur que {@link #emplacementBlocPlage()} lorsque le métrage = 10 cm). */
	String emplacementBlocNumero,
	/** {@link ommp.archives.entity.BoiteEtatType} — {@code SEMI_ACTIF}, {@code TRANSFERT}, {@code DESTRUCTION}. */
	String typeEtat,
	/** Libellé affiché (ex. Transféré, Détruit). */
	String typeEtatLabel,
	/** Date de l'état courant (ISO). */
	String dateEtat
) {
}
