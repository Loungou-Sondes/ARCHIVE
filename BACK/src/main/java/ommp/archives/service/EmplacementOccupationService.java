package ommp.archives.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ommp.archives.entity.Boite;
import ommp.archives.entity.BoiteEtat;
import ommp.archives.entity.Emplacement;
import ommp.archives.entity.TypeEmp;
import ommp.archives.repository.EmplacementRepository;

/**
 * Occupation physique des blocs : seule source de vérité {@code EMPLACEMENTS.ID_BOITE}.
 */
@Service
public class EmplacementOccupationService {

	private final EmplacementRepository emplacementRepository;

	public EmplacementOccupationService(EmplacementRepository emplacementRepository) {
		this.emplacementRepository = emplacementRepository;
	}

	public record OccupationPlage(String debutId, String finId, String plage, String numerosListe) {
	}

	@Transactional(readOnly = true)
	public List<Emplacement> blocsForBoite(Long boiteId) {
		if (boiteId == null) {
			return List.of();
		}
		return emplacementRepository.findByBoiteIdAndTypeEmpOrderByNumeroAsc(boiteId, TypeEmp.BLOC);
	}

	@Transactional(readOnly = true)
	public OccupationPlage resolvePlage(Long boiteId) {
		return fromBlocs(blocsForBoite(boiteId));
	}

	public static OccupationPlage fromBlocs(List<Emplacement> blocs) {
		if (blocs == null || blocs.isEmpty()) {
			return new OccupationPlage(null, null, null, null);
		}
		Emplacement debut = blocs.get(0);
		Emplacement fin = blocs.get(blocs.size() - 1);
		String plage = EmplacementPlageFormatter.formatRange(debut.getNumero(), fin.getNumero());
		String liste = blocs.stream().map(Emplacement::getNumero).collect(Collectors.joining(", "));
		return new OccupationPlage(debut.getId(), fin.getId(), plage, liste);
	}

	@Transactional(readOnly = true)
	public String resolveDisplay(Boite box, BoiteEtat etat) {
		if (box == null) {
			return null;
		}
		if (box.getId() != null) {
			String plage = resolvePlage(box.getId()).plage();
			if (plage != null) {
				return EmplacementPlageFormatter.formatPlage(plage);
			}
		}
		if (etat != null && etat.getPositionId() != null && !etat.getPositionId().isBlank()) {
			return EmplacementPlageFormatter.formatPlage(etat.getPositionId());
		}
		return null;
	}

	@Transactional(readOnly = true)
	public boolean isBlocOccupiedByOtherBoite(String blocId, Long excludeBoiteId) {
		return emplacementRepository.findById(blocId)
			.map(Emplacement::getBoiteId)
			.filter(occupant -> occupant != null)
			.filter(occupant -> excludeBoiteId == null || !occupant.equals(excludeBoiteId))
			.isPresent();
	}

	@Transactional(readOnly = true)
	public boolean hasBlocAssignment(Long boiteId) {
		return boiteId != null && !blocsForBoite(boiteId).isEmpty();
	}

	@Transactional
	public void releaseForBoite(Long boiteId) {
		if (boiteId == null) {
			return;
		}
		List<Emplacement> occupied = emplacementRepository.findByBoiteId(boiteId);
		if (occupied.isEmpty()) {
			return;
		}
		for (Emplacement emp : occupied) {
			emp.setBoiteId(null);
		}
		emplacementRepository.saveAll(occupied);
	}
}
