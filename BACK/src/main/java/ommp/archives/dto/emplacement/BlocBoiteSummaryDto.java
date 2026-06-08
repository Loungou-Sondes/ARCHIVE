package ommp.archives.dto.emplacement;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Résumé d’une boîte occupant un bloc d’emplacement. */
public record BlocBoiteSummaryDto(
	@JsonProperty("id") Long id,
	@JsonProperty("titre") String titre,
	@JsonProperty("anneeMin") int anneeMin,
	@JsonProperty("anneeMax") int anneeMax,
	@JsonProperty("metrageCm") int metrageCm,
	@JsonProperty("documentTypeTitle") String documentTypeTitle,
	@JsonProperty("motsCles") String motsCles,
	/** N° affiché du bordereau de transfert ({@code BORDEREAUX.NUMERO_AFFICHE}). */
	@JsonProperty("bordereauNumeroAffiche") String bordereauNumeroAffiche,
	@JsonProperty("bordereauId") Long bordereauId,
	/** Nombre de boîtes rattachées à ce bordereau. */
	@JsonProperty("bordereauBoiteCount") int bordereauBoiteCount,
	/**
	 * {@code true} si ce bordereau a au moins deux boîtes dont les emplacements ne forment pas
	 * un seul groupe contigu sur l’épi (même si une boîte occupe plusieurs blocs consécutifs).
	 */
	@JsonProperty("bordereauBoitesNonContigues") boolean bordereauBoitesNonContigues
) {
}
