package ommp.archives.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ommp.archives.dto.emplacement.BoiteBordereauNumeroProjection;
import ommp.archives.entity.Boite;

public interface BoiteRepository extends JpaRepository<Boite, Long>, JpaSpecificationExecutor<Boite> {

	@Query("""
		select e.typeEtat, count(b)
		from Boite b
		join b.etatCourant e
		group by e.typeEtat
		""")
	List<Object[]> countGroupByEtatCourantType();

	/** Lecture explicite de {@code BORDEREAUX.NUMERO_AFFICHE} pour la matrice (évite tout souci de double fetch). */
	@Query("""
		select b.id as boiteId, br.id as bordereauId, br.numeroAffiche as numeroAffiche, br.nombreBoites as nombreBoites
		from Boite b
		join b.bordereau br
		where b.id in :ids
		""")
	List<BoiteBordereauNumeroProjection> findBordereauNumeroAfficheByBoiteIdIn(@Param("ids") Collection<Long> ids);

	@Query("""
		select distinct b from Boite b
		join fetch b.documentType
		left join fetch b.bordereau
		where b.id in :ids
		""")
	List<Boite> findWithDocumentTypeByIdIn(Collection<Long> ids);

	/** Boîtes encore « sous » la règle valide du type (non figées sur une autre règle). */
	@Query("""
		select b from Boite b
		join fetch b.bordereau br
		where b.documentType.id = :documentTypeId
		and (b.conservationRule is null or b.conservationRule.id = :ruleId)
		order by br.numeroAffiche asc, b.titre asc
		""")
	List<Boite> findLinkedToConservationRuleForInvalidation(
		@Param("ruleId") Long ruleId,
		@Param("documentTypeId") Long documentTypeId
	);

	@Query("""
		select b from Boite b
		where b.documentType.id = :documentTypeId
		and (b.conservationRule is null or b.conservationRule.id = :ruleId)
		""")
	List<Boite> findLinkedToConservationRuleForInvalidationUpdate(
		@Param("ruleId") Long ruleId,
		@Param("documentTypeId") Long documentTypeId
	);

	@Query("""
		select b from Boite b
		join fetch b.documentType
		left join fetch b.conservationRule
		join fetch b.bordereau br
		where br.id in :bordereauIds
		""")
	List<Boite> findWithRuleContextByBordereauIdIn(@Param("bordereauIds") Collection<Long> bordereauIds);

	@Query("select b from Boite b where b.bordereau.id = :bordereauId")
	List<Boite> findByBordereauId(@Param("bordereauId") Long bordereauId);

	@Query("""
		select b from Boite b
		join fetch b.documentType
		left join fetch b.etatCourant
		where b.bordereau.id = :bordereauId
		order by b.id asc
		""")
	List<Boite> findWithDocumentTypeByBordereauId(@Param("bordereauId") Long bordereauId);

	long countByBordereauId(Long bordereauId);

	long countByBordereau_AgentId(String agentId);

	@Query("""
		select e.typeEtat, count(b)
		from Boite b
		join b.etatCourant e
		where b.bordereau.agent.id = :agentId
		group by e.typeEtat
		""")
	List<Object[]> countGroupByEtatCourantTypeForAgent(@Param("agentId") String agentId);

}
