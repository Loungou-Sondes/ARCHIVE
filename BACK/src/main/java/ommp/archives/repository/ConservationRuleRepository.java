package ommp.archives.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ommp.archives.entity.ConservationRule;
import ommp.archives.entity.ConservationRuleStatus;

public interface ConservationRuleRepository extends JpaRepository<ConservationRule, Long>, JpaSpecificationExecutor<ConservationRule> {

	@Query(
		value = """
			SELECT ID FROM REGLES_CONSERVATION
			WHERE ID_TYPE_DOCUMENT = :documentTypeId
			  AND UPPER(TRIM(STATUT)) = 'VALIDE'
			ORDER BY ID DESC
			FETCH FIRST 1 ROW ONLY
			""",
		nativeQuery = true
	)
	Optional<Long> findFirstValideRuleIdForDocumentType(@Param("documentTypeId") Long documentTypeId);

	Optional<ConservationRule> findFirstByDocumentType_IdAndStatusOrderByIdDesc(
		Long documentTypeId,
		ConservationRuleStatus status
	);

	@Query(
		value = "SELECT COUNT(*) FROM REGLES_CONSERVATION WHERE ID_TYPE_DOCUMENT = :documentTypeId AND UPPER(TRIM(STATUT)) = 'VALIDE'",
		nativeQuery = true
	)
	long countValideForDocumentType(@Param("documentTypeId") Long documentTypeId);

	@Query(
		value = "SELECT COUNT(*) FROM REGLES_CONSERVATION WHERE ID_TYPE_DOCUMENT = :documentTypeId "
			+ "AND UPPER(TRIM(STATUT)) = 'VALIDE' AND ID <> :excludeRuleId",
		nativeQuery = true
	)
	long countOtherValideForDocumentType(
		@Param("documentTypeId") Long documentTypeId,
		@Param("excludeRuleId") Long excludeRuleId
	);

	@Query(
		value = "SELECT REFERENCE_REGLE FROM REGLES_CONSERVATION WHERE ID_TYPE_DOCUMENT = :documentTypeId "
			+ "AND UPPER(TRIM(STATUT)) = 'VALIDE' ORDER BY ID FETCH FIRST 1 ROW ONLY",
		nativeQuery = true
	)
	List<String> findFirstValideReferenceForDocumentType(@Param("documentTypeId") Long documentTypeId);

	@Query(
		value = "SELECT REFERENCE_REGLE FROM REGLES_CONSERVATION WHERE ID_TYPE_DOCUMENT = :documentTypeId "
			+ "AND UPPER(TRIM(STATUT)) = 'VALIDE' AND ID <> :excludeRuleId ORDER BY ID FETCH FIRST 1 ROW ONLY",
		nativeQuery = true
	)
	List<String> findFirstOtherValideReferenceForDocumentType(
		@Param("documentTypeId") Long documentTypeId,
		@Param("excludeRuleId") Long excludeRuleId
	);

	/** Au plus une règle valide par couple (référence, type de document), insensible à la casse. */
	boolean existsByReferenceIgnoreCaseAndDocumentType_IdAndStatus(
		String reference,
		Long documentTypeId,
		ConservationRuleStatus status
	);

	boolean existsByReferenceIgnoreCaseAndDocumentType_IdAndIdNotAndStatus(
		String reference,
		Long documentTypeId,
		Long id,
		ConservationRuleStatus status
	);
}
