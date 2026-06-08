package ommp.archives.entity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Table {@code BORDEREAUX} — bordereau de transfert vers les archives.
 * <p>
 * PK : {@link #id} ({@code SEQ_ID_BORDEREAU}) — UK : {@link #numeroAffiche}
 * <p>
 * Relations :
 * <ul>
 *   <li>FK {@link #direction} → {@code DIRECTION.ID}</li>
 *   <li>FK {@link #agent} → {@code USERS.ID}</li>
 *   <li>1–N {@link #boites} → {@code BOITES.ID_BORDEREAU}</li>
 * </ul>
 * {@link #statut} pilote le flux emplacements ({@link BordereauStatut}), pas le cycle documentaire des boîtes
 * ({@link BoiteEtatType}).
 */
@Entity
@Table(
	name = "BORDEREAUX",
	uniqueConstraints = @UniqueConstraint(name = "UK_BORDEREAUX_NUMERO_AFFICHE", columnNames = { "NUMERO_AFFICHE" })
)
public class Bordereau {

	/** PK — séquence {@code SEQ_ID_BORDEREAU}. */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bordereau_seq")
	@SequenceGenerator(name = "bordereau_seq", sequenceName = "SEQ_ID_BORDEREAU", allocationSize = 1)
	@Column(name = "ID", nullable = false)
	private Long id;

	/** Rang dans l'année {@link #anneeNumero} (partie avant le tiret du numéro affiché). */
	@Column(name = "RANG_NUMERO", nullable = false)
	private int rangNumero;

	@Column(name = "ANNEE_NUMERO", nullable = false)
	private int anneeNumero;

	/** Numéro affiché unique (ex. {@code 1-2026}) = {@code RANG_NUMERO}-{@code ANNEE_NUMERO}. */
	@Column(name = "NUMERO_AFFICHE", length = 32, nullable = false)
	private String numeroAffiche;

	/** FK → {@code DIRECTION.ID} (optionnel). */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ID_DIRECTION", referencedColumnName = "ID")
	private Direction direction;

	@Column(name = "DATE_TRANSFERT", nullable = false)
	private LocalDate dateTransfert;

	@Column(name = "OBSERVATION", length = 2000)
	private String observation;

	/** {@link BordereauStatut} — colonne {@code STATUT}. */
	@Enumerated(EnumType.STRING)
	@Column(name = "STATUT", length = 32, nullable = false)
	private BordereauStatut statut = BordereauStatut.AFFECTE;

	/** FK → {@code USERS.ID} (agent créateur). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ID_AGENT", referencedColumnName = "ID", nullable = false)
	private UserAccount agent;

	/** 1–N — {@code BOITES.ID_BORDEREAU}, cascade ALL. */
	@OneToMany(mappedBy = "bordereau", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Boite> boites = new ArrayList<>();

	/** Dénormalisation : nombre de lignes {@link Boite} (maintenu par le service). */
	@Column(name = "NOMBRE_BOITES")
	private Integer nombreBoites = 0;

	/**
	 * Alerte agent « bordereau validé » : {@code false} = à afficher dans la cloche jusqu'à acquittement.
	 */
	@Column(name = "NOTIF_AGENT_VUE", nullable = false)
	private boolean notifAgentVue = true;

	public String getNumeroAffiche() {
		return numeroAffiche;
	}

	public void setNumeroAffiche(String numeroAffiche) {
		this.numeroAffiche = numeroAffiche;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public int getRangNumero() {
		return rangNumero;
	}

	public void setRangNumero(int rangNumero) {
		this.rangNumero = rangNumero;
	}

	public int getAnneeNumero() {
		return anneeNumero;
	}

	public void setAnneeNumero(int anneeNumero) {
		this.anneeNumero = anneeNumero;
	}

	public Direction getDirection() {
		return direction;
	}

	public void setDirection(Direction direction) {
		this.direction = direction;
	}

	public LocalDate getDateTransfert() {
		return dateTransfert;
	}

	public void setDateTransfert(LocalDate dateTransfert) {
		this.dateTransfert = dateTransfert;
	}

	public String getObservation() {
		return observation;
	}

	public void setObservation(String observation) {
		this.observation = observation;
	}

	public BordereauStatut getStatut() {
		return statut;
	}

	public void setStatut(BordereauStatut statut) {
		this.statut = statut;
	}

	public UserAccount getAgent() {
		return agent;
	}

	public void setAgent(UserAccount agent) {
		this.agent = agent;
	}

	public List<Boite> getBoites() {
		return boites;
	}

	public void setBoites(List<Boite> boites) {
		this.boites = boites;
	}

	public int getNombreBoites() {
		return nombreBoites == null ? 0 : nombreBoites;
	}

	public void setNombreBoites(int nombreBoites) {
		this.nombreBoites = nombreBoites;
	}

	public boolean isNotifAgentVue() {
		return notifAgentVue;
	}

	public void setNotifAgentVue(boolean notifAgentVue) {
		this.notifAgentVue = notifAgentVue;
	}
}
