package ommp.archives.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Table {@code ETATS} — historique du cycle documentaire d'une boîte (plusieurs lignes par boîte).
 * <p>
 * L'état <em>courant</em> n'est pas une colonne ici : {@link Boite} pointe la dernière ligne via
 * {@code BOITES.ETAT_COURANT_ID} → {@link #id}. Chaque {@code recordState} <strong>insère</strong> une ligne
 * puis met à jour ce pointeur.
 * <p>
 * PK : {@link #id} ({@code SEQ_ID_ETAT}) — FK : {@link #boite} → {@code BOITES.ID}
 * <p>
 * {@link #typeEtat} ∈ {@link BoiteEtatType} — distinct de {@link ConservationRule#getFinalDecision()}
 * (politique) et de {@link Bordereau#getStatut()} (flux emplacements).
 */
@Entity
@Table(name = "ETATS")
public class BoiteEtat {

	/** PK — séquence {@code SEQ_ID_ETAT}. */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "etat_seq")
	@SequenceGenerator(name = "etat_seq", sequenceName = "SEQ_ID_ETAT", allocationSize = 1)
	@Column(name = "ID", nullable = false)
	private Long id;

	/** FK → {@code BOITES.ID} (propriétaire de la relation historique). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "BOITE_ID", referencedColumnName = "ID", nullable = false)
	private Boite boite;

	/**
	 * Cycle documentaire réel : {@link BoiteEtatType#SEMI_ACTIF},
	 * {@link BoiteEtatType#TRANSFERT} ou {@link BoiteEtatType#DESTRUCTION}.
	 * CHECK Oracle : {@code CK_ETATS_TYPE_ETAT}.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "TYPE_ETAT", nullable = false, length = 50)
	private BoiteEtatType typeEtat;

	/** Date métier de l'événement (jour civil). */
	@Column(name = "DATE_ETAT", nullable = false)
	private LocalDate dateEtat;

	/** Horodatage technique de l'insertion. */
	@Column(name = "DATE_CREATION")
	private Instant dateCreation;

	/** Compte ayant déclenché l'événement. */
	@Column(name = "UTILISATEUR", length = 255)
	private String utilisateur;

	/** Libellé libre (création, assignation, approbation, …). */
	@Column(name = "COMMENTAIRE", length = 255)
	private String commentaire;

	/** Copie de la plage d'emplacement au moment de l'événement (blocs {@code EMPLACEMENTS.ID_BOITE}). */
	@Column(name = "POSITION_ID", length = 255)
	private String positionId;

	/** Copie de {@code REGLES_CONSERVATION.ID} en vigueur à l'événement (sans FK JPA). */
	@Column(name = "REGLE_CONSERVATION_ID")
	private Long regleConservationId;

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

	public BoiteEtatType getTypeEtat() {
		return typeEtat;
	}

	public void setTypeEtat(BoiteEtatType typeEtat) {
		this.typeEtat = typeEtat;
	}

	public LocalDate getDateEtat() {
		return dateEtat;
	}

	public void setDateEtat(LocalDate dateEtat) {
		this.dateEtat = dateEtat;
	}

	public Instant getDateCreation() {
		return dateCreation;
	}

	public void setDateCreation(Instant dateCreation) {
		this.dateCreation = dateCreation;
	}

	public String getUtilisateur() {
		return utilisateur;
	}

	public void setUtilisateur(String utilisateur) {
		this.utilisateur = utilisateur;
	}

	public String getCommentaire() {
		return commentaire;
	}

	public void setCommentaire(String commentaire) {
		this.commentaire = commentaire;
	}

	public String getPositionId() {
		return positionId;
	}

	public void setPositionId(String positionId) {
		this.positionId = positionId;
	}

	public Long getRegleConservationId() {
		return regleConservationId;
	}

	public void setRegleConservationId(Long regleConservationId) {
		this.regleConservationId = regleConservationId;
	}
}
