package ommp.archives.ml;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Normalisation de texte pour la recherche (latin + arabe).
 */
public final class TextNormalizer {

	private static final Pattern NON_WORD = Pattern.compile(
		"[^\\p{L}\\p{N}\\sàâäéèêëïîôùûüç-]",
		Pattern.UNICODE_CASE
	);
	private static final Pattern ARABIC_SCRIPT = Pattern.compile("\\p{Script=Arabic}");

	private static final Set<String> LATIN_STOP_WORDS = Set.of(
		"a", "au", "aux", "avec", "ce", "ces", "dans", "de", "des", "du", "elle", "en", "et",
		"eux", "il", "je", "la", "le", "les", "leur", "lui", "ma", "mais", "me", "meme", "mes",
		"moi", "mon", "ne", "nos", "notre", "nous", "on", "ou", "par", "pas", "pour", "qu", "que",
		"qui", "sa", "se", "ses", "son", "sur", "ta", "te", "tes", "toi", "ton", "tu", "un", "une",
		"vos", "votre", "vous", "y", "est", "sont", "ete", "etre", "avoir", "fait", "faire",
		"plus", "tout", "tous", "toute", "toutes", "cette", "cet", "cela", "comme", "donc", "ainsi",
		"entre", "vers", "chez", "sans", "sous", "apres", "avant", "pendant", "depuis", "lors",
		"the", "and", "or", "of", "to", "in", "for", "at", "by"
	);

	private static final Set<String> ARABIC_STOP_WORDS = Set.of(
		"في", "من", "على", "الى", "عن", "مع", "هذا", "هذه", "ذلك", "تلك", "التي", "الذي",
		"هو", "هي", "هم", "هن", "ان", "كان", "كانت", "ما", "لا", "لم", "لن", "قد", "كل",
		"بين", "حتى", "عند", "او", "ثم", "بل", "اذ", "اذا"
	);

	private TextNormalizer() {
	}

	public static boolean containsArabicScript(String text) {
		return text != null && ARABIC_SCRIPT.matcher(text).find();
	}

	public static boolean isStopWord(String token) {
		if (token == null || token.isBlank()) {
			return true;
		}
		String norm = normalize(token);
		if (containsArabicScript(norm)) {
			return ARABIC_STOP_WORDS.contains(norm);
		}
		return LATIN_STOP_WORDS.contains(norm);
	}

	public static String normalize(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}
		String lower = text.toLowerCase(Locale.ROOT);
		String noAccent = Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
		String unified = unifyArabicLetters(noAccent);
		return NON_WORD.matcher(unified).replaceAll(" ").replaceAll("\\s+", " ").trim();
	}

	private static String unifyArabicLetters(String text) {
		StringBuilder sb = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			switch (ch) {
				case '\u0622', '\u0623', '\u0625', '\u0671' -> sb.append('\u0627');
				case '\u0629' -> sb.append('\u0647');
				case '\u0649' -> sb.append('\u064A');
				case '\u0640' -> { /* tatweel ignoré */ }
				default -> sb.append(ch);
			}
		}
		return sb.toString();
	}
}
