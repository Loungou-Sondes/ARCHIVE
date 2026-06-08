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
 * Table unique {@code AUDIT_LOG} — journal des mutations API et connexions (pas les GET).
 * <p>
 * Schéma géré par Hibernate ({@code spring.jpa.hibernate.ddl-auto=update}), comme les autres entités.
 * Séquence Oracle : {@code SEQ_AUDIT_LOG}.
 */
@Entity
@Table(name = "AUDIT_LOG")
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_log_seq")
	@SequenceGenerator(name = "audit_log_seq", sequenceName = "SEQ_AUDIT_LOG", allocationSize = 1)
	@Column(name = "ID", nullable = false)
	private Long id;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	@Column(name = "USER_NAME", length = 255)
	private String userName;

	@Column(name = "ACTION_CODE", length = 64, nullable = false)
	private String actionCode;

	@Column(name = "RESOURCE_TYPE", length = 32)
	private String resourceType;

	@Column(name = "DETAIL", length = 500)
	private String detail;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getActionCode() {
		return actionCode;
	}

	public void setActionCode(String actionCode) {
		this.actionCode = actionCode;
	}

	public String getResourceType() {
		return resourceType;
	}

	public void setResourceType(String resourceType) {
		this.resourceType = resourceType;
	}

	public String getDetail() {
		return detail;
	}

	public void setDetail(String detail) {
		this.detail = detail;
	}
}
