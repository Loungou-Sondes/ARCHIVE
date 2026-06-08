package ommp.archives.auth;

import java.util.Arrays;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class SessionCookieService {

	@Value("${app.session.cookie-name}")
	private String cookieName;

	@Value("${app.session.secure-cookie:false}")
	private boolean secureCookie;

	public void writeSessionCookie(HttpServletResponse response, String rawToken) {
		Cookie cookie = new Cookie(cookieName, rawToken);
		cookie.setHttpOnly(true);
		cookie.setSecure(secureCookie);
		cookie.setPath("/");
		response.addCookie(cookie);
	}

	public void clearSessionCookie(HttpServletResponse response) {
		Cookie cookie = new Cookie(cookieName, "");
		cookie.setHttpOnly(true);
		cookie.setSecure(secureCookie);
		cookie.setPath("/");
		cookie.setMaxAge(0);
		response.addCookie(cookie);
	}

	public Optional<String> readSessionToken(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		return Arrays.stream(cookies)
			.filter(c -> cookieName.equals(c.getName()))
			.map(Cookie::getValue)
			.filter(v -> v != null && !v.isBlank())
			.findFirst();
	}
}
