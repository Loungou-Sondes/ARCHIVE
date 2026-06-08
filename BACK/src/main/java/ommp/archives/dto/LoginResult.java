package ommp.archives.dto;

public record LoginResult(
	LoginResponse response,
	String sessionToken
) {
}
