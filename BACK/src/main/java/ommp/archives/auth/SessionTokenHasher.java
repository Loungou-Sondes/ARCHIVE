package ommp.archives.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

public final class SessionTokenHasher {

	private static final SecureRandom RANDOM = new SecureRandom();

	private SessionTokenHasher() {
	}

	public static String generateRawToken() {
		byte[] bytes = new byte[48];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder(hashed.length * 2);
			for (byte b : hashed) {
				sb.append(String.format("%02x", b));
			}
			return sb.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 unavailable", ex);
		}
	}
}
