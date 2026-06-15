package ommp.archives.ml;

/**
 * Correspondance approximative (fautes de frappe, variantes proches).
 */
public final class FuzzyMatcher {

	private FuzzyMatcher() {
	}

	public static boolean isSimilar(String left, String right) {
		if (left == null || right == null) {
			return false;
		}
		if (left.equals(right)) {
			return true;
		}
		int minLen = Math.min(left.length(), right.length());
		int maxLen = Math.max(left.length(), right.length());
		if (minLen < 3) {
			return left.startsWith(right) || right.startsWith(left);
		}
		if (left.startsWith(right) || right.startsWith(left)) {
			return true;
		}
		int maxDistance = maxEditDistance(minLen);
		return levenshtein(left, right) <= maxDistance && maxLen - minLen <= maxDistance;
	}

	static int maxEditDistance(int termLength) {
		if (termLength <= 4) {
			return 1;
		}
		if (termLength <= 7) {
			return 2;
		}
		return 3;
	}

	static int levenshtein(String left, String right) {
		int[] prev = new int[right.length() + 1];
		int[] curr = new int[right.length() + 1];
		for (int j = 0; j <= right.length(); j++) {
			prev[j] = j;
		}
		for (int i = 1; i <= left.length(); i++) {
			curr[0] = i;
			for (int j = 1; j <= right.length(); j++) {
				int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
				curr[j] = Math.min(
					Math.min(curr[j - 1] + 1, prev[j] + 1),
					prev[j - 1] + cost
				);
			}
			int[] swap = prev;
			prev = curr;
			curr = swap;
		}
		return prev[right.length()];
	}
}
