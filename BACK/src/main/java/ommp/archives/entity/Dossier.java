package ommp.archives.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Dossier documentaire rattaché à une {@link Boite} (table {@code DOSSIERS}, FK {@code BOITE_ID}).
 */
@Entity
@Table(name = "DOSSIERS")
public class Dossier {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "dossier_seq")
	@SequenceGenerator(name = "dossier_seq", sequenceName = "SEQ_ID_DOSSIER", allocationSize = 1)
	@Column(name = "ID", nullable = false)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "BOITE_ID", referencedColumnName = "ID", nullable = false)
	private Boite boite;

	@Column(name = "TITRE", length = 500, nullable = false)
	private String titre;

	@Column(name = "CONTENU", length = 4000)
	private String contenu;

	@Column(name = "ANNEE", nullable = false)
	private int annee;

	@Column(name = "CREATED_AT", nullable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Boite getBoite() {
		return boite;
	}

	public void setBoite(Boite boite) {
		this.boite = boite;
	}

	public String getTitre() {
		return titre;
	}

	public void setTitre(String titre) {
		this.titre = titre;
	}

	public String getContenu() {
		return contenu;
	}

	public void setContenu(String contenu) {
		this.contenu = contenu;
	}

	public int getAnnee() {
		return annee;
	}

	public void setAnnee(int annee) {
		this.annee = annee;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}
