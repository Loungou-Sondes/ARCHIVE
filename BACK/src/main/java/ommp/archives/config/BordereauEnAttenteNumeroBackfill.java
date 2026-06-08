package ommp.archives.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ommp.archives.entity.Bordereau;
import ommp.archives.entity.BordereauStatut;
import ommp.archives.repository.BordereauRepository;
import ommp.archives.service.BordereauNumeroSupport;

/**
 * Bordereaux {@code EN_ATTENTE} : numéro provisoire {@code ATT-{id}} (pas de numéro officiel consommé).
 */
@Component
public class BordereauEnAttenteNumeroBackfill implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BordereauEnAttenteNumeroBackfill.class);

	private final BordereauRepository bordereauRepository;

	public BordereauEnAttenteNumeroBackfill(BordereauRepository bordereauRepository) {
		this.bordereauRepository = bordereauRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		int updated = 0;
		for (Bordereau b : bordereauRepository.findAll()) {
			if (b.getStatut() != BordereauStatut.EN_ATTENTE || b.getId() == null) {
				continue;
			}
			String expected = BordereauNumeroSupport.PENDING_PREFIX + b.getId();
			String current = b.getNumeroAffiche();
			boolean alreadyOk = expected.equals(current) && b.getRangNumero() == 0;
			boolean legacyLongPlaceholder = current != null
				&& current.startsWith("ATT-PENDING-")
				&& current.length() > BordereauNumeroSupport.NUMERO_AFFICHE_MAX_LENGTH;
			if (alreadyOk && !legacyLongPlaceholder) {
				continue;
			}
			b.setAnneeNumero(b.getDateTransfert().getYear());
			b.setRangNumero(0);
			b.setNumeroAffiche(expected);
			updated++;
		}
		if (updated > 0) {
			log.info("Backfill numéros EN_ATTENTE : {} bordereau(x) passés en référence provisoire ATT-{{id}}.", updated);
		}
	}

}
