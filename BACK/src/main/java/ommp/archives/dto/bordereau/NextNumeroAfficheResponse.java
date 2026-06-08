package ommp.archives.dto.bordereau;

/**
 * Aperçu du numéro qui sera attribué au prochain bordereau pour une année donnée
 * (même logique qu’à l’enregistrement : {@code rang + 1}-{année}).
 */
public record NextNumeroAfficheResponse(String numeroAffiche) {
}
