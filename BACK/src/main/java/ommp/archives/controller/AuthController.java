package ommp.archives.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import ommp.archives.auth.SessionCookieService;
import ommp.archives.dto.LoginRequest;
import ommp.archives.dto.LoginResponse;
import ommp.archives.dto.LoginResult;
import ommp.archives.dto.UpdateUserProfileRequest;
import ommp.archives.dto.UserInfoResponse;
import ommp.archives.dto.UserProfileResponse;
import ommp.archives.service.AuthService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final SessionCookieService sessionCookieService;

    public AuthController(AuthService authService, SessionCookieService sessionCookieService) {
        this.authService = authService;
        this.sessionCookieService = sessionCookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletResponse response
    ) {
        LoginResult result = authService.login(request);
        sessionCookieService.writeSessionCookie(response, result.sessionToken());
        return ResponseEntity.ok(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> me(Authentication authentication) {
        return ResponseEntity.ok(authService.currentUser(authentication));
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileResponse> profile(Authentication authentication) {
        return ResponseEntity.ok(authService.currentUserProfile(authentication));
    }

    @PutMapping("/profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
        Authentication authentication,
        @Valid @RequestBody UpdateUserProfileRequest request
    ) {
        return ResponseEntity.ok(authService.updateCurrentUserProfile(authentication, request));
    }
}
