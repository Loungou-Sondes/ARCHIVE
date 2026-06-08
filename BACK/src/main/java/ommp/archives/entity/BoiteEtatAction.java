package ommp.archives.entity;

/**
 * Événement passé à {@link ommp.archives.service.BoiteEtatService#recordState} — détermine la
 * prochaine valeur {@code ETATS.TYPE_ETAT} (voir {@link #toTypeEtat()}).
 */
public enum BoiteEtatAction {

	/** Création boîte → {@link BoiteEtatType#SEMI_ACTIF}. */
	CREATION(BoiteEtatType.SEMI_ACTIF),

	/** Affectation / réaffectation blocs → reste {@link BoiteEtatType#SEMI_ACTIF}. */
	ASSIGNATION(BoiteEtatType.SEMI_ACTIF),

	/** Approbation échéance transfert → {@link BoiteEtatType#TRANSFERT}. */
	APPROBATION_TRANSFERT(BoiteEtatType.TRANSFERT),

	/** Approbation échéance destruction → {@link BoiteEtatType#DESTRUCTION}. */
	APPROBATION_DESTRUCTION(BoiteEtatType.DESTRUCTION);

	private final BoiteEtatType typeEtat;

	BoiteEtatAction(BoiteEtatType typeEtat) {
		this.typeEtat = typeEtat;
	}

	/** Valeur écrite dans {@code ETATS.TYPE_ETAT}. */
	public BoiteEtatType toTypeEtat() {
		return typeEtat;
	}
}
