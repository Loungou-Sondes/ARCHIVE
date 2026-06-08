package ommp.archives.dto.bordereau;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** JSON + Bean Validation sur record : {@link JsonProperty} évite l’échec Jackson sur le constructeur canonique. */
public record CreateBordereauBoiteRequest(
	@JsonProperty("titre") @NotBlank @Size(max = 500) String titre,
	@JsonProperty("anneeMin") @NotNull @Min(1900) @Max(2100) Integer anneeMin,
	@JsonProperty("anneeMax") @NotNull @Min(1900) @Max(2100) Integer anneeMax,
	@JsonProperty("metrageCm") @NotNull Integer metrageCm,
	@JsonProperty("contenu") @Size(max = 2000) String contenu,
	@JsonProperty("motsCles") @NotBlank @Size(max = 500) String motsCles,
	@JsonProperty("documentTypeId") @NotNull Long documentTypeId,
	/**
	 * Blocs à occuper (consécutifs sur une tablette). Nombre = {@code metrageCm / 10}
	 * Liste des blocs consécutifs (30 cm ⇒ 3 blocs…). Persistée comme début/fin/plage métier {@code NUMERO_DEBUT-NUMERO_FIN} sur BOITES ({@link ommp.archives.entity.Boite}).
	 */
	@JsonProperty("emplacementBlocIds") List<@Size(max = 36) String> emplacementBlocIds,
	/** Rétrocompatibilité : un seul bloc si la liste est absente. */
	@JsonProperty("emplacementBlocId") @Size(max = 36) String emplacementBlocId,
	/**
	 * {@code true} si l’affectation en deux segments (non contigüs sur la même tablette) pour cette boîte est acceptée
	 * (bouton « assigner automatiquement » après suggestion avec {@link BoiteBlocsSuggestionDto#splitRecommendation()}).
	 */
	@JsonProperty("acceptSplitBlocAssignment") Boolean acceptSplitBlocAssignment,
	/**
	 * {@code true} si l’affectation sur <strong>plusieurs tablettes</strong> pour cette boîte est acceptée
	 * (bouton après suggestion {@link ommp.archives.dto.bordereau.BoiteBlocsSuggestionDto#multiTabletteRecommendation()}).
	 */
	@JsonProperty("acceptMultiTabletteBlocAssignment") Boolean acceptMultiTabletteBlocAssignment,
	/**
	 * Obligatoire si la règle valide du type de document a {@code activeUnknown = true} :
	 * nombre d'années actives renseigné au moment du rattachement de la boîte.
	 */
	@JsonProperty("renseignerAnneesActives") Integer renseignerAnneesActives
) {
}
