package ommp.archives.dto.bordereau;

import java.util.List;

/**
 * Proposition d’affectation pour une boîte : {@code n} blocs consécutifs sur une tablette ;
 * sinon deux plages sur la <strong>même</strong> tablette ; sinon deux segments sur <strong>deux tablettes différentes</strong>
 * ({@link #multiTabletteRecommendation()}, opt-in distinct).
 */
public record BoiteBlocsSuggestionDto(
	int metrageCm,
	int blocsRequis,
	List<BlocEmplacementSuggestionDto> blocs,
	/** {@code true} si deux plages sur la même tablette, séparées par des bloc(s) occupés. Mutuellement exclusif avec {@link #multiTabletteRecommendation()}. */
	boolean splitRecommendation,
	/** Si segmentation (split ou multi-tablettes), nombre de blocs du premier segment. Sinon {@code null}. */
	Integer premierePlageBlocs,
	/** {@code true} si le premier segment est sur une tablette et la suite sur une autre ({@link #premierePlageBlocs()} &lt; {@link #blocsRequis}). */
	boolean multiTabletteRecommendation
) {
}
