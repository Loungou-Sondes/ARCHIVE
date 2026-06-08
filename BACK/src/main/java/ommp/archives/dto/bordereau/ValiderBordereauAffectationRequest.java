package ommp.archives.dto.bordereau;

import java.util.List;

/**
 * Optionnel : si absent ou vide, utilise les blocs enregistrés à la création « en attente ».
 */
public record ValiderBordereauAffectationRequest(List<ValiderBoiteAffectationItem> boites) {
}
