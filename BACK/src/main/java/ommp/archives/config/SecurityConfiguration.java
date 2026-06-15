package ommp.archives.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import ommp.archives.auth.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

	@Value("${app.cors.allowed-origins}")
	private String allowedOrigins;

	private final JwtAuthenticationFilter jwtAuthenticationFilter;

	public SecurityConfiguration(JwtAuthenticationFilter jwtAuthenticationFilter) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.csrf(AbstractHttpConfigurer::disable)
			.cors(c -> c.configurationSource(corsConfigurationSource()))
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.anonymous(a -> a.disable())
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/password-reset-request/public").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/password-reset-eligibility/public").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/password-reset-complete/public").permitAll()

				// —— Modules réservés administrateur (défense HTTP, complète les services) ——
				.requestMatchers("/api/dashboard/stats").hasRole("ADMIN")
				.requestMatchers("/api/agents", "/api/agents/**").hasRole("ADMIN")
				.requestMatchers("/api/emplacements/**").hasRole("ADMIN")
				.requestMatchers("/api/conservation-rules/**").hasRole("ADMIN")
				.requestMatchers("/api/document-types/**").hasRole("ADMIN")
				.requestMatchers("/api/alertes/**").hasRole("ADMIN")
				.requestMatchers(
					"/api/boites/alertes-archives",
					"/api/boites/alertes-semi-actif",
					"/api/boites/alertes-echeance-destruction-transfert"
				).hasRole("ADMIN")
				.requestMatchers("/api/boites/archives-intermediaires/**").hasRole("ADMIN")
				.requestMatchers("/api/boites/historique").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/api/boites/*/reporter-alerte-semi-actif").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/api/boites/*/approuver-destruction-transfert").hasRole("ADMIN")
				.requestMatchers("/api/audit/**").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/api/auth/users/*/approve-password-reset").hasRole("ADMIN")
				.requestMatchers("/api/bordereaux/validation-agents").hasRole("ADMIN")
				.requestMatchers("/api/bordereaux/suggestion-emplacements-blocs").hasRole("ADMIN")
				.requestMatchers("/api/bordereaux/blocs-libres").hasRole("ADMIN")
				.requestMatchers(HttpMethod.POST, "/api/bordereaux/*/valider-affectation").hasRole("ADMIN")

				// —— Authentifié (agent ou admin) : périmètre affiné dans les services ——
				.requestMatchers("/api/**").authenticated()
				.anyRequest().permitAll())
			.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		List<String> origins = Arrays.stream(allowedOrigins.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.collect(Collectors.toList());
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(origins);
		configuration.setAllowCredentials(true);
		configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));
		configuration.setExposedHeaders(List.of("Authorization"));
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}
}
