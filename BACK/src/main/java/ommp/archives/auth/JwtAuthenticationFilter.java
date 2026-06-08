package ommp.archives.auth;

import java.io.IOException;
import java.util.Optional;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ommp.archives.service.UserSessionService;

/**
 * Authentifie chaque requête via le cookie de session (jeton opaque validé en base).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final UserSessionService userSessionService;
	private final SessionCookieService sessionCookieService;
	private final UserDetailsService userDetailsService;

	public JwtAuthenticationFilter(
		UserSessionService userSessionService,
		SessionCookieService sessionCookieService,
		UserDetailsService userDetailsService
	) {
		this.userSessionService = userSessionService;
		this.sessionCookieService = sessionCookieService;
		this.userDetailsService = userDetailsService;
	}

	@Override
	protected void doFilterInternal(
		@NonNull HttpServletRequest request,
		@NonNull HttpServletResponse response,
		@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		Optional<String> rawToken = sessionCookieService.readSessionToken(request);
		if (rawToken.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
			try {
				userSessionService.validateAndTouch(rawToken.get()).ifPresent(username -> {
					UserDetails userDetails = userDetailsService.loadUserByUsername(username);
					if (userDetails.isEnabled()) {
						UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
							userDetails,
							null,
							userDetails.getAuthorities());
						auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
						SecurityContextHolder.getContext().setAuthentication(auth);
					}
				});
			} catch (RuntimeException ignored) {
				// Session invalide : requête non authentifiée
			}
		}
		filterChain.doFilter(request, response);
	}
}
