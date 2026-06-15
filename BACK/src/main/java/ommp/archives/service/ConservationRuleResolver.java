package ommp.archives.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import ommp.archives.entity.ConservationRule;
import ommp.archives.entity.ConservationRuleStatus;
import ommp.archives.repository.ConservationRuleRepository;

/**
 * Résolution des règles valides — requête ID native (Oracle {@code UPPER(TRIM(STATUT))}) puis chargement JPA.
 */
@Service
public class ConservationRuleResolver {

	private final ConservationRuleRepository conservationRuleRepository;

	public ConservationRuleResolver(ConservationRuleRepository conservationRuleRepository) {
		this.conservationRuleRepository = conservationRuleRepository;
	}

	public boolean hasValideRuleForDocumentType(Long documentTypeId) {
		if (documentTypeId == null) {
			return false;
		}
		return conservationRuleRepository.countValideForDocumentType(documentTypeId) > 0;
	}

	public Optional<ConservationRule> findValideRuleForDocumentType(Long documentTypeId) {
		if (documentTypeId == null) {
			return Optional.empty();
		}
		Optional<Long> ruleId = conservationRuleRepository.findFirstValideRuleIdForDocumentType(documentTypeId);
		if (ruleId.isPresent()) {
			return conservationRuleRepository.findById(ruleId.get());
		}
		return conservationRuleRepository.findFirstByDocumentType_IdAndStatusOrderByIdDesc(
			documentTypeId,
			ConservationRuleStatus.VALIDE
		);
	}
}
