package ommp.archives.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ommp.archives.auth.SessionTokenHasher;
import ommp.archives.entity.UserSession;
import ommp.archives.repository.UserSessionRepository;

@Service
public class UserSessionService {

	private final UserSessionRepository userSessionRepository;

	@Value("${app.session.max-age-seconds}")
	private long maxAgeSeconds;

	@Value("${app.session.idle-timeout-minutes}")
	private long idleTimeoutMinutes;

	public UserSessionService(UserSessionRepository userSessionRepository) {
		this.userSessionRepository = userSessionRepository;
	}

	@Transactional
	public String createSession(String userName) {
		revokeAllForUser(userName);
		String rawToken = SessionTokenHasher.generateRawToken();
		Instant now = Instant.now();
		UserSession session = new UserSession();
		session.setTokenHash(SessionTokenHasher.hash(rawToken));
		session.setUserName(userName);
		session.setCreatedAt(now);
		session.setExpiresAt(now.plusSeconds(maxAgeSeconds));
		session.setLastActivityAt(now);
		session.setRevoked(false);
		userSessionRepository.save(session);
		return rawToken;
	}

	@Transactional
	public Optional<String> validateAndTouch(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return Optional.empty();
		}
		return userSessionRepository.findByTokenHashAndRevokedFalse(SessionTokenHasher.hash(rawToken))
			.filter(this::isActive)
			.map(session -> {
				session.setLastActivityAt(Instant.now());
				userSessionRepository.save(session);
				return session.getUserName();
			});
	}

	@Transactional
	public void revoke(String rawToken) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		userSessionRepository.findByTokenHashAndRevokedFalse(SessionTokenHasher.hash(rawToken))
			.ifPresent(session -> {
				session.setRevoked(true);
				userSessionRepository.save(session);
			});
	}

	public long getMaxAgeSeconds() {
		return maxAgeSeconds;
	}

	private boolean isActive(UserSession session) {
		Instant now = Instant.now();
		if (!session.getExpiresAt().isAfter(now)) {
			return false;
		}
		return session.getLastActivityAt()
			.plus(Duration.ofMinutes(idleTimeoutMinutes))
			.isAfter(now);
	}

	private void revokeAllForUser(String userName) {
		List<UserSession> active = userSessionRepository.findByUserNameAndRevokedFalse(userName);
		for (UserSession session : active) {
			session.setRevoked(true);
		}
		if (!active.isEmpty()) {
			userSessionRepository.saveAll(active);
		}
	}
}
