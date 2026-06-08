package ommp.archives.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	@Value("${app.jwt.secret}")
	private String secret;

	@Value("${app.jwt.expiration-ms}")
	private long expirationMs;

	private SecretKey signingKey() {
		byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
		return Keys.hmacShaKeyFor(bytes);
	}

	public String generateToken(UserDetails userDetails) {
		Map<String, Object> claims = new HashMap<>();
		List<String> roles = userDetails.getAuthorities().stream()
			.map(GrantedAuthority::getAuthority)
			.collect(Collectors.toList());
		claims.put("roles", roles);
		return Jwts.builder()
			.claims(claims)
			.subject(userDetails.getUsername())
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + expirationMs))
			.signWith(signingKey())
			.compact();
	}

	public String extractUsername(String token) {
		return parseClaims(token).getSubject();
	}

	public boolean isTokenValid(String token, UserDetails userDetails) {
		String subject = extractUsername(token);
		return subject.equals(userDetails.getUsername()) && !isExpired(token);
	}

	private boolean isExpired(String token) {
		return parseClaims(token).getExpiration().before(new Date());
	}

	private Claims parseClaims(String token) {
		return Jwts.parser()
			.verifyWith(signingKey())
			.build()
			.parseSignedClaims(token)
			.getPayload();
	}

	public long getExpirationSeconds() {
		return expirationMs / 1000;
	}
}

