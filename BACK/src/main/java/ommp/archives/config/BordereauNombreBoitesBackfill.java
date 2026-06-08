package ommp.archives.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ommp.archives.entity.Bordereau;
import ommp.archives.repository.BoiteRepository;
import ommp.archives.repository.BordereauRepository;

/**
 * Au démarrage, initialise {@code NOMBRE_BOITES} uniquement lorsqu’il vaut 0 (ne pas écraser une valeur déjà renseignée).
 */
@Component
public class BordereauNombreBoitesBackfill implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BordereauNombreBoitesBackfill.class);

	private final BordereauRepository bordereauRepository;
	private final BoiteRepository boiteRepository;

	public BordereauNombreBoitesBackfill(BordereauRepository bordereauRepository, BoiteRepository boiteRepository) {
		this.bordereauRepository = bordereauRepository;
		this.boiteRepository = boiteRepository;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		int updated = 0;
		for (Bordereau b : bordereauRepository.findAll()) {
			if (b.getId() == null) {
				continue;
			}
			if (b.getNombreBoites() > 0) {
				continue;
			}
			int fromBoites = (int) boiteRepository.countByBordereauId(b.getId());
			if (fromBoites > 0) {
				b.setNombreBoites(fromBoites);
				updated++;
			}
		}
		if (updated > 0) {
			log.info("Backfill BORDEREAUX.NOMBRE_BOITES : {} bordereau(x) mis à jour.", updated);
		}
	}
}
