package ommp.archives.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

/**
 * Table {@code DOCUMENT_TYPES} — référentiel des types de documents archivés.
 * <p>
 * PK : {@link #id} ({@code SEQ_DOCUMENT_TYPE_ID}) — FK optionnelle : {@link #direction} → {@code DIRECTION.ID}
 * <p>
 * Lié en 1–N logique aux {@link ConservationRule} ({@code REGLES_CONSERVATION.ID_TYPE_DOCUMENT})
 * et aux {@link Boite} ({@code BOITES.ID_TYPE_DOCUMENT}).
 */
@Entity
@Table(name = "DOCUMENT_TYPES")
public class DocumentType {

	/** PK — séquence {@code SEQ_DOCUMENT_TYPE_ID}. */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "document_type_seq")
	@SequenceGenerator(name = "document_type_seq", sequenceName = "SEQ_DOCUMENT_TYPE_ID", allocationSize = 1)
	@Column(name = "ID", nullable = false)
	private Long id;

	@Column(name = "TITLE", length = 500, nullable = false)
	private String title;

	/** FK → {@code DIRECTION.ID} (optionnel). */
	@ManyToOne(fetch = FetchType.EAGER, optional = true)
	@JoinColumn(name = "DIRECTION_ID", referencedColumnName = "ID", nullable = true)
	private Direction direction;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public Direction getDirection() {
		return direction;
	}

	public void setDirection(Direction direction) {
		this.direction = direction;
	}
}
