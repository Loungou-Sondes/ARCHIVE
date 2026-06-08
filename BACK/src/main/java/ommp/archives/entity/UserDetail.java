package ommp.archives.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Table {@code USER_DETAILS} — fiche agent (données RH / organisation).
 * <p>
 * PK : {@link #registrationNumber} (matricule).
 * <p>
 * Liens logiques (sans FK JPA) :
 * <ul>
 *   <li>{@link UserAccount#getUserRegistrationNumber()} = {@link #registrationNumber}</li>
 *   <li>{@link #directionId} → {@code DIRECTION.ID}</li>
 * </ul>
 * <p>
 * Les comptes sont créés en base par l'entreprise ; l'application ne gère que la consultation
 * et l'activation / désactivation ({@link #statusId}).
 */
@Entity
@Table(name = "USER_DETAILS")
public class UserDetail {

	@Id
	@Column(name = "REGISTRATION_NUMBER", length = 255, nullable = false)
	private String registrationNumber;

	@Column(name = "BIRTH_DATE")
	private Instant birthDate;

	@Column(name = "CIN", length = 255)
	private String cin;

	@Column(name = "FIRST_NAME", length = 255)
	private String firstName;

	@Column(name = "LAST_NAME", length = 255)
	private String lastName;

	@Column(name = "PHONE_NUMBER", length = 255)
	private String phoneNumber;

	@Column(name = "RECRUITMENT_DATE")
	private Instant recruitmentDate;

	@Column(name = "JOB_ID")
	private Integer jobId;

	@Column(name = "POSITION_ID", length = 255)
	private String positionId;

	@Column(name = "STATUS_ID")
	private Long statusId;

	@Column(name = "DIRECTION_ID", length = 255)
	private String directionId;

	public String getRegistrationNumber() {
		return registrationNumber;
	}

	public void setRegistrationNumber(String registrationNumber) {
		this.registrationNumber = registrationNumber;
	}

	public Instant getBirthDate() {
		return birthDate;
	}

	public void setBirthDate(Instant birthDate) {
		this.birthDate = birthDate;
	}

	public String getCin() {
		return cin;
	}

	public void setCin(String cin) {
		this.cin = cin;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public Instant getRecruitmentDate() {
		return recruitmentDate;
	}

	public void setRecruitmentDate(Instant recruitmentDate) {
		this.recruitmentDate = recruitmentDate;
	}

	public Integer getJobId() {
		return jobId;
	}

	public void setJobId(Integer jobId) {
		this.jobId = jobId;
	}

	public String getPositionId() {
		return positionId;
	}

	public void setPositionId(String positionId) {
		this.positionId = positionId;
	}

	public Long getStatusId() {
		return statusId;
	}

	public void setStatusId(Long statusId) {
		this.statusId = statusId;
	}

	public String getDirectionId() {
		return directionId;
	}

	public void setDirectionId(String directionId) {
		this.directionId = directionId;
	}
}
