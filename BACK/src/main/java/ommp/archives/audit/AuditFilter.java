package ommp.archives.audit;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ommp.archives.service.AuditService;

/**
 * Enregistre un audit après chaque mutation API réussie (POST, PUT, PATCH, DELETE).
 * Les GET ne sont pas journalisés (audit léger).
 */
@Component
public class AuditFilter extends OncePerRequestFilter {

	private static final Set<String> MUTATING = Set.of(
		HttpMethod.POST.name(),
		HttpMethod.PUT.name(),
		HttpMethod.PATCH.name(),
		HttpMethod.DELETE.name()
	);

	private final AuditService auditService;
	private final AuditMutationResolver mutationResolver;

	public AuditFilter(AuditService auditService, AuditMutationResolver mutationResolver) {
		this.auditService = auditService;
		this.mutationResolver = mutationResolver;
	}

	@Override
	protected void doFilterInternal(
		@NonNull HttpServletRequest request,
		@NonNull HttpServletResponse response,
		@NonNull FilterChain filterChain
	) throws ServletException, IOException {
		filterChain.doFilter(request, response);
		if (!shouldAudit(request, response)) {
			return;
		}
		AuditEntry entry = mutationResolver.resolve(request.getMethod(), request.getRequestURI());
		if (entry != null) {
			auditService.recordFromSecurityContext(entry);
		}
	}

	private boolean shouldAudit(HttpServletRequest request, HttpServletResponse response) {
		String method = request.getMethod();
		if (method == null || !MUTATING.contains(method.toUpperCase())) {
			return false;
		}
		int status = response.getStatus();
		if (status < 200 || status >= 300) {
			return false;
		}
		String uri = request.getRequestURI();
		if (uri == null || !uri.startsWith("/api/")) {
			return false;
		}
		String path = uri.contains("?") ? uri.substring(0, uri.indexOf('?')) : uri;
		if ("/api/auth/login".equalsIgnoreCase(path)) {
			return false;
		}
		if (path.startsWith("/api/audit")) {
			return false;
		}
		return true;
	}
}
