package ommp.archives.ml;



import java.util.ArrayList;

import java.util.Comparator;

import java.util.HashMap;

import java.util.HashSet;

import java.util.LinkedHashMap;

import java.util.LinkedHashSet;

import java.util.List;

import java.util.Map;

import java.util.Set;



/**

 * Similarité TF-IDF + cosinus, correspondance partielle et fuzzy (sans modèle entraîné).

 */

public final class TextSimilarityEngine {



	private TextSimilarityEngine() {

	}



	public static List<ScoredDocument> rank(String query, List<SearchDocument> documents, int limit) {

		if (query == null || query.isBlank() || documents == null || documents.isEmpty()) {

			return List.of();

		}

		List<String> queryTerms = queryTerms(query);

		if (queryTerms.isEmpty()) {

			return List.of();

		}



		List<List<String>> corpusTokens = new ArrayList<>();

		for (SearchDocument doc : documents) {

			corpusTokens.add(tokenize(doc.text()));

		}



		List<String> expandedTerms = expandQueryTerms(queryTerms, corpusTokens);



		Map<String, Integer> documentFrequency = new HashMap<>();

		for (List<String> tokens : corpusTokens) {

			for (String token : Set.copyOf(tokens)) {

				documentFrequency.merge(token, 1, Integer::sum);

			}

		}



		int n = documents.size();

		Map<String, Double> queryVector = vectorize(expandedTerms, corpusTokens, documentFrequency, n);



		List<ScoredDocument> scored = new ArrayList<>();

		for (int i = 0; i < documents.size(); i++) {

			String docText = documents.get(i).text();

			List<String> docTokens = corpusTokens.get(i);

			double tfidf = cosine(queryVector, vectorize(docTokens, corpusTokens, documentFrequency, n));

			double partial = partialMatchScore(queryTerms, docText, docTokens);

			double score = Math.max(tfidf, partial);

			if (score > 0.0) {

				scored.add(new ScoredDocument(documents.get(i).id(), score));

			}

		}



		scored.sort(Comparator.comparingDouble(ScoredDocument::score).reversed());

		if (limit > 0 && scored.size() > limit) {

			return scored.subList(0, limit);

		}

		return scored;

	}



	/**

	 * Étend la requête avec des termes proches du vocabulaire indexé (fautes, variantes).

	 */

	static List<String> expandQueryTerms(List<String> queryTerms, List<List<String>> corpusTokens) {

		Set<String> vocabulary = new HashSet<>();

		for (List<String> tokens : corpusTokens) {

			vocabulary.addAll(tokens);

		}

		LinkedHashSet<String> expanded = new LinkedHashSet<>(queryTerms);

		for (String term : queryTerms) {

			if (term.length() < 3) {

				continue;

			}

			for (String vocabTerm : vocabulary) {

				if (!term.equals(vocabTerm) && FuzzyMatcher.isSimilar(term, vocabTerm)) {

					expanded.add(vocabTerm);

				}

			}

		}

		return List.copyOf(expanded);

	}



	/**

	 * Correspondance partielle, préfixe et fuzzy sur le texte et le vocabulaire du document.

	 */

	static double partialMatchScore(List<String> queryTerms, String documentText, List<String> docTokens) {

		if (queryTerms.isEmpty() || documentText == null || documentText.isBlank()) {

			return 0.0;

		}

		String normDoc = TextNormalizer.normalize(documentText);

		String[] docWords = normDoc.split("\\s+");

		double matched = 0.0;

		for (String term : queryTerms) {

			double termScore = matchTerm(term, normDoc, docWords, docTokens);

			matched += termScore;

		}

		return matched / queryTerms.size();

	}



	private static double matchTerm(String term, String normDoc, String[] docWords, List<String> docTokens) {

		if (term.length() < 2) {

			return 0.0;

		}

		if (normDoc.contains(term)) {

			return 1.0;

		}

		for (String word : docWords) {

			if (word.length() < 2) {

				continue;

			}

			if (word.startsWith(term) || term.startsWith(word)) {

				return 0.9;

			}

			if (word.length() >= term.length() + 2 && word.contains(term)) {

				return 0.75;

			}

		}

		for (String word : docWords) {

			if (word.length() >= 3 && FuzzyMatcher.isSimilar(term, word)) {

				return 0.7;

			}

		}

		for (String token : docTokens) {

			if (token.length() >= 3 && FuzzyMatcher.isSimilar(term, token)) {

				return 0.65;

			}

		}

		return 0.0;

	}



	static List<String> queryTerms(String query) {

		List<String> tokens = tokenize(query);

		if (!tokens.isEmpty()) {

			return tokens;

		}

		String norm = TextNormalizer.normalize(query).trim();

		if (norm.length() >= 2 && !TextNormalizer.isStopWord(norm)) {

			return List.of(norm);

		}

		return List.of();

	}



	public static String buildDocumentText(

		String titre,

		String contenu,

		String motsCles,

		String documentTypeTitle,

		String directionLabel,

		String bordereauNumero,

		Integer anneeMin,

		Integer anneeMax

	) {

		StringBuilder sb = new StringBuilder();

		append(sb, titre);

		append(sb, contenu);

		append(sb, motsCles);

		append(sb, documentTypeTitle);

		append(sb, directionLabel);

		append(sb, bordereauNumero);

		if (anneeMin != null) {

			append(sb, String.valueOf(anneeMin));

		}

		if (anneeMax != null && !anneeMax.equals(anneeMin)) {

			append(sb, String.valueOf(anneeMax));

		}

		return sb.toString().trim();

	}



	private static void append(StringBuilder sb, String value) {

		if (value != null && !value.isBlank()) {

			if (!sb.isEmpty()) {

				sb.append(' ');

			}

			sb.append(value.trim());

		}

	}



	private static Map<String, Double> vectorize(

		List<String> tokens,

		List<List<String>> corpusTokens,

		Map<String, Integer> documentFrequency,

		int documentCount

	) {

		Map<String, Integer> termFrequency = new HashMap<>();

		for (String token : tokens) {

			termFrequency.merge(token, 1, Integer::sum);

		}

		Map<String, Double> vector = new LinkedHashMap<>();

		for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {

			String term = entry.getKey();

			double tf = entry.getValue();

			int df = documentFrequency.getOrDefault(term, 0);

			double idf = Math.log((documentCount + 1.0) / (df + 1.0)) + 1.0;

			vector.put(term, tf * idf);

		}

		return vector;

	}



	private static double cosine(Map<String, Double> a, Map<String, Double> b) {

		double dot = 0.0;

		for (Map.Entry<String, Double> entry : a.entrySet()) {

			Double other = b.get(entry.getKey());

			if (other != null) {

				dot += entry.getValue() * other;

			}

		}

		double normA = 0.0;

		for (double v : a.values()) {

			normA += v * v;

		}

		double normB = 0.0;

		for (double v : b.values()) {

			normB += v * v;

		}

		if (normA == 0.0 || normB == 0.0) {

			return 0.0;

		}

		return dot / (Math.sqrt(normA) * Math.sqrt(normB));

	}



	static List<String> tokenize(String text) {

		if (text == null || text.isBlank()) {

			return List.of();

		}

		String normalized = TextNormalizer.normalize(text);

		String[] parts = normalized.split("\\s+");

		List<String> tokens = new ArrayList<>();

		for (String part : parts) {

			if (part.length() < 2) {

				continue;

			}

			if (TextNormalizer.isStopWord(part)) {

				continue;

			}

			tokens.add(part);

		}

		return tokens;

	}



	public record SearchDocument(String id, String text) {

	}



	public record ScoredDocument(String id, double score) {

	}

}


