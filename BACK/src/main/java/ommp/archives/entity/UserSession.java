package ommp.archives.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Sessions applicatives stockées en base (jeton opaque, hashé).
 * Le navigateur ne reçoit que l’identifiant brut via cookie httpOnly.
 */
@Entity
@Table(name = "USER_SESSIONS")
public class UserSession {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_session_seq")
	@SequenceGenerator(name = "user_session_seq", sequenceName = "SEQ_USER_SESSIONS", allocationSize = 1)
	@Column(name = "ID", nullable = false)
	private Long id;

	@Column(name = "TOKEN_HASH", length = 64, nullable = false, unique = true)
	private String tokenHash;

	@Column(name = "USER_NAME", length = 255, nullable = false)
	private String userName;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	@Column(name = "EXPIRES_AT", nullable = false)
	private Instant expiresAt;

	@Column(name = "LAST_ACTIVITY_AT", nullable = false)
	private Instant lastActivityAt;

	@Column(name = "REVOKED", nullable = false)
	private boolean revoked;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public void setTokenHash(String tokenHash) {
		this.tokenHash = tokenHash;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public void setExpiresAt(Instant expiresAt) {
		this.expiresAt = expiresAt;
	}

	public Instant getLastActivityAt() {
		return lastActivityAt;
	}

	public void setLastActivityAt(Instant lastActivityAt) {
		this.lastActivityAt = lastActivityAt;
	}

	public boolean isRevoked() {
		return revoked;
	}

	public void setRevoked(boolean revoked) {
		this.revoked = revoked;
	}
}
