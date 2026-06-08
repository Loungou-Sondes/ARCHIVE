package ommp.archives.service;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ommp.archives.entity.Boite;
import ommp.archives.entity.BoiteEtat;
import ommp.archives.entity.BoiteEtatAction;
import ommp.archives.entity.BoiteEtatType;
import ommp.archives.repository.BoiteEtatRepository;
import ommp.archives.repository.BoiteRepository;

/**
 * Cycle documentaire des boîtes ({@code ETATS} + {@code BOITES.ETAT_COURANT_ID}).
 * <p>Ne gère pas le flux bordereau ({@code BORDEREAUX.STATUT}) ni les règles de conservation.
 * @see ommp.archives.entity.BoiteEtatType
 */
@Service
public class BoiteEtatService {

	private final BoiteEtatRepository boiteEtatRepository;
	private final BoiteRepository boiteRepository;
	private final EmplacementOccupationService emplacementOccupationService;

	public BoiteEtatService(
		BoiteEtatRepository boiteEtatRepository,
		BoiteRepository boiteRepository,
		EmplacementOccupationService emplacementOccupationService
	) {
		this.boiteEtatRepository = boiteEtatRepository;
		this.boiteRepository = boiteRepository;
		this.emplacementOccupationService = emplacementOccupationService;
	}

	/**
	 * Enregistre un nouvel état (ligne dans {@code ETATS}) et met à jour {@link Boite#setEtatCourant}.
	 * Appel unique pour création, assignation et décision de fin de vie.
	 */
	@Transactional
	public BoiteEtat recordState(Boite box, BoiteEtatAction action, String utilisateur, String commentaire) {
		if (box == null || box.getId() == null) {
			throw new IllegalArgumentException("Boîte persistée requise pour enregistrer un état.");
		}
		BoiteEtatType type = action.toTypeEtat();
		String positionSnapshot = emplacementOccupationService.resolveDisplay(box, null);
		if (type == BoiteEtatType.TRANSFERT || type == BoiteEtatType.DESTRUCTION) {
			libererEmplacementsPhysiques(box);
		}

		BoiteEtat etat = new BoiteEtat();
		etat.setBoite(box);
		etat.setTypeEtat(type);
		etat.setDateEtat(LocalDate.now());
		etat.setDateCreation(Instant.now());
		etat.setUtilisateur(utilisateur);
		etat.setCommentaire(commentaire);
		etat.setPositionId(positionSnapshot);
		if (box.getConservationRule() != null) {
			etat.setRegleConservationId(box.getConservationRule().getId());
		}

		etat = boiteEtatRepository.save(etat);
		box.getEtats().add(etat);
		box.setEtatCourant(etat);
		boiteRepository.save(box);
		return etat;
	}

	/** Libère les blocs physiques liés à la boîte ({@code EMPLACEMENTS.ID_BOITE}). */
	@Transactional
	public void libererEmplacementsPhysiques(Boite box) {
		if (box == null || box.getId() == null) {
			return;
		}
		emplacementOccupationService.releaseForBoite(box.getId());
	}

	@Transactional
	public void clearEtatsForBoite(Boite box) {
		if (box == null || box.getId() == null) {
			return;
		}
		box.setEtatCourant(null);
		box.getEtats().clear();
		boiteRepository.saveAndFlush(box);
		boiteEtatRepository.deleteByBoiteId(box.getId());
	}
}
