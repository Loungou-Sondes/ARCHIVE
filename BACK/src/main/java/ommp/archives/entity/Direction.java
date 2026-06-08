package ommp.archives.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Table {@code DIRECTION} — référentiel des directions (données métier, pas de création via l'API archives).
 * <p>
 * PK : {@link #id} (chaîne métier, pas de séquence).
 * <p>
 * Référencée par : {@link DocumentType} ({@code DIRECTION_ID}),
 * {@link Bordereau} ({@code ID_DIRECTION}),
 * {@link UserDetail} ({@code DIRECTION_ID}, lien logique sans JPA).
 */
@Entity
@Table(name = "DIRECTION")
public class Direction {

	/** PK — code direction. */
	@Id
	@Column(name = "ID", length = 64, nullable = false)
	private String id;

	@Column(name = "LABEL", length = 255, nullable = false)
	private String label;

	public Direction() {
	}

	public Direction(String id, String label) {
		this.id = id;
		this.label = label;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}
}
