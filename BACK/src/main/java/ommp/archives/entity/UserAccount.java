package ommp.archives.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Table {@code USERS} — comptes de connexion (authentification).
 * <p>
 * PK : {@link #id} — UK : {@link #userName}
 * <p>
 * Lien logique vers {@link UserDetail} : {@link #userRegistrationNumber} =
 * {@code USER_DETAILS.REGISTRATION_NUMBER}.
 * Référencé par {@link Bordereau} ({@code BORDEREAUX.ID_AGENT}).
 * <p>
 * Ne pas exposer telle quelle en JSON API (entité persistence uniquement).
 */
@Entity
@Table(name = "USERS")
public class UserAccount {

	/** PK — identifiant compte. */
	@Id
	@Column(name = "ID", length = 255, nullable = false)
	private String id;

	@Column(name = "EMAIL", length = 255)
	private String email;

	@Column(name = "GENDER")
	private Integer gender;

	@Column(name = "PASSWORD", length = 255)
	private String password;

	@Column(name = "PHONE_NUMBER", length = 255)
	private String phoneNumber;

	@Column(name = "ROLE", length = 255)
	private String role;

	@Column(name = "USER_NAME", length = 255, nullable = false, unique = true)
	private String userName;

	/** Clé de jointure logique → {@link UserDetail#getRegistrationNumber()}. */
	@Column(name = "USER_REGISTRATION_NUMBER", length = 255)
	private String userRegistrationNumber;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public Integer getGender() {
		return gender;
	}

	public void setGender(Integer gender) {
		this.gender = gender;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getPhoneNumber() {
		return phoneNumber;
	}

	public void setPhoneNumber(String phoneNumber) {
		this.phoneNumber = phoneNumber;
	}

	public String getRole() {
		return role;
	}

	public void setRole(String role) {
		this.role = role;
	}

	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public String getUserRegistrationNumber() {
		return userRegistrationNumber;
	}

	public void setUserRegistrationNumber(String userRegistrationNumber) {
		this.userRegistrationNumber = userRegistrationNumber;
	}
}
