package ommp.archives.entity;

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
 * Table {@code REGLES_CONSERVATION} — politique de conservation par type de document.
 * <p>
 * PK : {@link #id} ({@code SEQ_ID_REGLE_CONSERV}) — FK : {@link #documentType} → {@code DOCUMENT_TYPES.ID}
 * <p>
 * Au plus une ligne {@link ConservationRuleStatus#VALIDE} par type de document (contrôle service).
 * {@link #finalDecision} ({@link FinalDecision}) est la <em>politique</em>, distincte de
 * {@link BoiteEtatType} (ce qui s'est réellement passé sur une boîte).
 */
@Entity
@Table(name = "REGLES_CONSERVATION")
public class ConservationRule {

	/** PK — séquence {@code SEQ_ID_REGLE_CONSERV}. */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "regle_conservation_seq")
	@SequenceGenerator(name = "regle_conservation_seq", sequenceName = "SEQ_ID_REGLE_CONSERV", allocationSize = 1)
	@Column(name = "ID", nullable = false)
	private Long id;

	/** Référence métier — au plus une règle {@link ConservationRuleStatus#VALIDE} par couple (référence, type), insensible à la casse. */
	@Column(name = "REFERENCE_REGLE", length = 120, nullable = false)
	private String reference;

	@Column(name = "ACTIF_INCONNU", nullable = false)
	private boolean activeUnknown;

	@Column(name = "ANNEES_ACTIF")
	private Integer activeYears;

	@Column(name = "SEMI_ACTIF_INCONNU", nullable = false)
	private boolean semiActiveUnknown;

	@Column(name = "ANNEES_SEMI_ACTIF")
	private Integer semiActiveYears;

	/** {@link FinalDecision} — colonne {@code DECISION_FINALE}. */
	@Enumerated(EnumType.STRING)
	@Column(name = "DECISION_FINALE", length = 32, nullable = false)
	private FinalDecision finalDecision;

	/** {@link ConservationRuleStatus} — colonne {@code STATUT}. */
	@Enumerated(EnumType.STRING)
	@Column(name = "STATUT", length = 32, nullable = false)
	private ConservationRuleStatus status = ConservationRuleStatus.VALIDE;

	/** FK → {@code DOCUMENT_TYPES.ID}. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ID_TYPE_DOCUMENT", referencedColumnName = "ID", nullable = false)
	private DocumentType documentType;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getReference() {
		return reference;
	}

	public void setReference(String reference) {
		this.reference = reference;
	}

	public boolean isActiveUnknown() {
		return activeUnknown;
	}

	public void setActiveUnknown(boolean activeUnknown) {
		this.activeUnknown = activeUnknown;
	}

	public Integer getActiveYears() {
		return activeYears;
	}

	public void setActiveYears(Integer activeYears) {
		this.activeYears = activeYears;
	}

	public boolean isSemiActiveUnknown() {
		return semiActiveUnknown;
	}

	public void setSemiActiveUnknown(boolean semiActiveUnknown) {
		this.semiActiveUnknown = semiActiveUnknown;
	}

	public Integer getSemiActiveYears() {
		return semiActiveYears;
	}

	public void setSemiActiveYears(Integer semiActiveYears) {
		this.semiActiveYears = semiActiveYears;
	}

	public FinalDecision getFinalDecision() {
		return finalDecision;
	}

	public void setFinalDecision(FinalDecision finalDecision) {
		this.finalDecision = finalDecision;
	}

	public ConservationRuleStatus getStatus() {
		return status;
	}

	public void setStatus(ConservationRuleStatus status) {
		this.status = status;
	}

	public DocumentType getDocumentType() {
		return documentType;
	}

	public void setDocumentType(DocumentType documentType) {
		this.documentType = documentType;
	}
}
