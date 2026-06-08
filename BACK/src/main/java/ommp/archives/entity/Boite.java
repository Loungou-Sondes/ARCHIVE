package ommp.archives.entity;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Table {@code BOITES} — contenu archivé rattaché à un {@link Bordereau}.
 * <p>
 * <b>Deux axes indépendants :</b>
 * <ol>
 *   <li><b>Flux bordereau / emplacements</b> — {@link Bordereau#getStatut()} {@code EN_ATTENTE | AFFECTE}
 *       + occupation via {@link Emplacement#getBoiteId()} sur les blocs ({@code EMPLACEMENTS.ID_BOITE}).</li>
 *   <li><b>Cycle documentaire</b> — table {@link BoiteEtat} ({@code ETATS}), pointeur {@link #etatCourant}
 *       ({@code ETAT_COURANT_ID}). Valeurs : {@link BoiteEtatType}.</li>
 * </ol>
 * PK : {@link #id} ({@code SEQ_ID_BOITE}).
 */
@Entity
@EntityListeners(BoiteEntityListener.class)
@Table(name = "BOITES")
public class Boite {

	/** PK — séquence {@code SEQ_ID_BOITE}. */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "boite_seq")
	@SequenceGenerator(name = "boite_seq", sequenceName = "SEQ_ID_BOITE", allocationSize = 1)
	@Column(name = "ID", nullable = false)
	private Long id;

	/** FK → {@code BORDEREAUX.ID}. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ID_BORDEREAU", referencedColumnName = "ID", nullable = false)
	private Bordereau bordereau;

	@Column(name = "TITRE", length = 500, nullable = false)
	private String titre;

	@Column(name = "ANNEE_MIN", nullable = false)
	private int anneeMin;

	@Column(name = "ANNEE_MAX", nullable = false)
	private int anneeMax;

	@Column(name = "METRAGE_CM", nullable = false)
	private int metrageCm;

	@Column(name = "CONTENU", length = 2000)
	private String contenu;

	@Column(name = "MOTS_CLES", length = 500)
	private String motsCles;

	/** FK → {@code TYPES_DOCUMENT.ID}. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ID_TYPE_DOCUMENT", referencedColumnName = "ID", nullable = false)
	private DocumentType documentType;

	/** FK → {@code REGLES_CONSERVATION.ID} (politique ; peut être une règle invalidée). */
	@ManyToOne(fetch = FetchType.LAZY, optional = true)
	@JoinColumn(name = "ID_REGLE_CONSERVATION", referencedColumnName = "ID")
	private ConservationRule conservationRule;

	/**
	 * Année d'affichage de l'alerte « durée semi-active inconnue ».
	 * {@code null} = alerte visible immédiatement ; valeur future = masquée jusqu'à cette année.
	 */
	@Column(name = "ALERTE_SEMI_ACTIF_ANNEE_AFFICHAGE")
	private Integer semiActifAlerteAnneeAffichage;

	/**
	 * FK → {@code ETATS.ID} — dernière ligne d'historique = état documentaire courant.
	 * Mis à jour par {@link ommp.archives.service.BoiteEtatService#recordState}.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ETAT_COURANT_ID", referencedColumnName = "ID")
	private BoiteEtat etatCourant;

	/**
	 * Historique {@code ETATS} (1–N). {@code mappedBy = "boite"} : FK {@code BOITE_ID} côté {@link BoiteEtat}.
	 * Cascade ALL + orphanRemoval : suppression de la boîte supprime ses lignes d'état.
	 */
	@OneToMany(mappedBy = "boite", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<BoiteEtat> etats = new ArrayList<>();

	/** Dossiers documentaires (1–N) — table {@code DOSSIERS}. */
	@OneToMany(mappedBy = "boite", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<Dossier> dossiers = new ArrayList<>();

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Bordereau getBordereau() {
		return bordereau;
	}

	public void setBordereau(Bordereau bordereau) {
		this.bordereau = bordereau;
	}

	public String getTitre() {
		return titre;
	}

	public void setTitre(String titre) {
		this.titre = titre;
	}

	public int getAnneeMin() {
		return anneeMin;
	}

	public void setAnneeMin(int anneeMin) {
		this.anneeMin = anneeMin;
	}

	public int getAnneeMax() {
		return anneeMax;
	}

	public void setAnneeMax(int anneeMax) {
		this.anneeMax = anneeMax;
	}

	public int getMetrageCm() {
		return metrageCm;
	}

	public void setMetrageCm(int metrageCm) {
		this.metrageCm = metrageCm;
	}

	public String getContenu() {
		return contenu;
	}

	public void setContenu(String contenu) {
		this.contenu = contenu;
	}

	public String getMotsCles() {
		return motsCles;
	}

	public void setMotsCles(String motsCles) {
		this.motsCles = motsCles;
	}

	public DocumentType getDocumentType() {
		return documentType;
	}

	public void setDocumentType(DocumentType documentType) {
		this.documentType = documentType;
	}

	public ConservationRule getConservationRule() {
		return conservationRule;
	}

	public void setConservationRule(ConservationRule conservationRule) {
		this.conservationRule = conservationRule;
	}

	public Integer getSemiActifAlerteAnneeAffichage() {
		return semiActifAlerteAnneeAffichage;
	}

	public void setSemiActifAlerteAnneeAffichage(Integer semiActifAlerteAnneeAffichage) {
		this.semiActifAlerteAnneeAffichage = semiActifAlerteAnneeAffichage;
	}

	public BoiteEtat getEtatCourant() {
		return etatCourant;
	}

	public void setEtatCourant(BoiteEtat etatCourant) {
		this.etatCourant = etatCourant;
	}

	public List<BoiteEtat> getEtats() {
		return etats;
	}

	public void setEtats(List<BoiteEtat> etats) {
		this.etats = etats;
	}

	public List<Dossier> getDossiers() {
		return dossiers;
	}

	public void setDossiers(List<Dossier> dossiers) {
		this.dossiers = dossiers;
	}
}
