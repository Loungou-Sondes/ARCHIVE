package ommp.archives.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Table {@code EMPLACEMENTS} — grille physique (épi → travée → tablette → bloc).
 * <p>
 * PK : {@link #id} (UUID / chaîne, pas de séquence).
 * <p>
 * Pas de FK JPA vers parent : la hiérarchie est encodée dans {@link #numero}.
 * Occupation d'un bloc : {@link #boiteId} → {@code BOITES.ID} ({@code null} = libre).
 * Seule source de vérité pour l'occupation physique (plage début/fin dérivée des blocs liés).
 */
@Entity
@Table(name = "EMPLACEMENTS")
public class Emplacement {

	/** PK — identifiant technique (souvent UUID). */
	@Id
	@Column(name = "ID", length = 36, nullable = false)
	private String id;

	/** Code métier hiérarchique (épi, travée, tablette, bloc). */
	@Column(name = "NUMERO", length = 255, nullable = false)
	private String numero;

	/** {@link TypeEmp} — colonne {@code TYPE_EMP}. */
	@Enumerated(EnumType.STRING)
	@Column(name = "TYPE_EMP", length = 20, nullable = false)
	private TypeEmp typeEmp;

	/** Métrage du nœud (surtout pour blocs / tablettes). */
	@Column(name = "METRAGE")
	private Double metrage;

	/**
	 * Référence logique → {@code BOITES.ID} (sans {@code @ManyToOne}).
	 * Uniquement pour {@link TypeEmp#BLOC} : {@code null} = bloc libre.
	 */
	@Column(name = "ID_BOITE")
	private Long boiteId;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getNumero() {
		return numero;
	}

	public void setNumero(String numero) {
		this.numero = numero;
	}

	public TypeEmp getTypeEmp() {
		return typeEmp;
	}

	public void setTypeEmp(TypeEmp typeEmp) {
		this.typeEmp = typeEmp;
	}

	public Double getMetrage() {
		return metrage;
	}

	public void setMetrage(Double metrage) {
		this.metrage = metrage;
	}

	public Long getBoiteId() {
		return boiteId;
	}

	public void setBoiteId(Long boiteId) {
		this.boiteId = boiteId;
	}
}
