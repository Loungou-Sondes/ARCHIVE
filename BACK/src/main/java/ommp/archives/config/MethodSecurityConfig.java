package ommp.archives.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/** Active {@code @PreAuthorize} / {@code @PostAuthorize} sur les contrôleurs si besoin futur. */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}
