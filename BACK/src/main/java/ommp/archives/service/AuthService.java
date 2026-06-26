package ommp.archives.service;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ommp.archives.audit.AuditAction;
import ommp.archives.audit.AuditEntry;
import ommp.archives.audit.AuditResourceType;
import ommp.archives.auth.SessionCookieService;
import ommp.archives.dto.LoginRequest;
import ommp.archives.dto.LoginResponse;
import ommp.archives.dto.LoginResult;
import ommp.archives.dto.UpdateUserProfileRequest;
import ommp.archives.dto.UserInfoResponse;
import ommp.archives.dto.UserProfileResponse;
import ommp.archives.entity.UserAccount;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.UserAccountRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final UserSessionService userSessionService;
    private final SessionCookieService sessionCookieService;
    private final UserAccountRepository userAccountRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService auditService;
    private final AuthorizationService authorization;

    /** Colonnes optionnelles (si absentes en base, pas d’erreur SQL / Hibernate). */
    private boolean userTableHasHarborColumn;

    public AuthService(
        AuthenticationManager authenticationManager,
        UserDetailsService userDetailsService,
        UserSessionService userSessionService,
        SessionCookieService sessionCookieService,
        UserAccountRepository userAccountRepository,
        JdbcTemplate jdbcTemplate,
        AuditService auditService,
        AuthorizationService authorization
    ) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.userSessionService = userSessionService;
        this.sessionCookieService = sessionCookieService;
        this.userAccountRepository = userAccountRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.authorization = authorization;
    }

    @PostConstruct
    void detectOptionalUserColumns() {
        try {
            userTableHasHarborColumn = oracleColumnExists("USERS", "HARBOR");
        } catch (RuntimeException ignored) {
            userTableHasHarborColumn = false;
        }
    }

    /** Détection Oracle ({@code USER_TAB_COLUMNS}) ; renvoie false si la vue n’existe pas (ex. H2 en test). */
    private boolean oracleColumnExists(String tableName, String columnName) {
        try {
            Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class,
                tableName.toUpperCase(),
                columnName.toUpperCase());
            return n != null && n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Libellé port affiché en UI — colonne optionnelle {@code USERS.HARBOR}. */
    @Transactional(readOnly = true)
    public String readPortForUser(String userId) {
        if (!userTableHasHarborColumn || userId == null || userId.isBlank()) {
            return null;
        }
        try {
            String value = jdbcTemplate.queryForObject(
                "SELECT HARBOR FROM USERS WHERE ID = ?",
                String.class,
                userId.trim());
            return cleanNullable(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Identifiants utilisateurs dont le port ({@code HARBOR}) correspond à la recherche texte. */
    @Transactional(readOnly = true)
    public List<String> findUserIdsByHarborLike(String q) {
        if (!userTableHasHarborColumn || q == null || q.isBlank()) {
            return List.of();
        }
        String like = "%" + q.trim().toLowerCase() + "%";
        try {
            return jdbcTemplate.queryForList(
                "SELECT ID FROM USERS WHERE HARBOR IS NOT NULL AND LOWER(HARBOR) LIKE ?",
                String.class,
                like);
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String username = request.username();
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, request.password()));
            UserDetails user = userDetailsService.loadUserByUsername(username);
            String sessionToken = userSessionService.createSession(user.getUsername());
            List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
            auditService.record(
                username,
                new AuditEntry(
                    AuditAction.LOGIN_SUCCESS,
                    AuditResourceType.AUTH,
                    "Connexion réussie"
                )
            );
            LoginResponse response = new LoginResponse(
                userSessionService.getMaxAgeSeconds(),
                user.getUsername(),
                roles);
            return new LoginResult(response, sessionToken);
        } catch (AuthenticationException ex) {
            auditService.record(
                username,
                new AuditEntry(
                    AuditAction.LOGIN_FAILED,
                    AuditResourceType.AUTH,
                    "Échec de connexion"
                )
            );
            throw ex;
        }
    }

    @Transactional
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessionCookieService.readSessionToken(request).ifPresent(userSessionService::revoke);
        sessionCookieService.clearSessionCookie(response);
    }

    @Transactional(readOnly = true)
    public UserInfoResponse currentUser(Authentication authentication) {
        authorization.requireAuthenticated(authentication);
        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());
        return new UserInfoResponse(authentication.getName(), roles);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse currentUserProfile(Authentication authentication) {
        authorization.requireAuthenticated(authentication);
        UserAccount account = findByUsernameOrThrow(authentication.getName());
        return toProfileResponse(account);
    }

    @Transactional
    public UserProfileResponse updateCurrentUserProfile(Authentication authentication, UpdateUserProfileRequest request) {
        authorization.requireAuthenticated(authentication);
        UserAccount account = findByUsernameOrThrow(authentication.getName());
        applyEditableFields(account, request);
        userAccountRepository.save(account);
        return toProfileResponse(account);
    }

    private void applyEditableFields(UserAccount account, UpdateUserProfileRequest request) {
        String email = cleanNullable(request.email());
        String phoneNumber = cleanNullable(request.phoneNumber());

        if (email != null) {
            account.setEmail(email);
        }
        if (phoneNumber != null) {
            account.setPhoneNumber(phoneNumber);
        }
    }

    private String cleanNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** N’écrase la valeur en base que si une valeur non vide est fournie (évite null implicites JSON). */
    private void applyIfPresent(String raw, Consumer<String> setter) {
        String v = cleanNullable(raw);
        if (v != null) {
            setter.accept(v);
        }
    }

    private UserProfileResponse toProfileResponse(UserAccount account) {
        return new UserProfileResponse(
            account.getUserName(),
            account.getEmail(),
            account.getPhoneNumber(),
            account.getRole()
        );
    }

    private UserAccount findByUsernameOrThrow(String userName) {
        return userAccountRepository
            .findByUserName(userName)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Utilisateur introuvable."));
    }
}
