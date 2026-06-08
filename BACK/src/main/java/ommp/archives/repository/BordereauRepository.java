package ommp.archives.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ommp.archives.entity.Bordereau;
import ommp.archives.entity.BordereauStatut;

public interface BordereauRepository extends JpaRepository<Bordereau, Long>, JpaSpecificationExecutor<Bordereau> {

	long countByStatut(BordereauStatut statut);

	long countByDateTransfertBetween(LocalDate from, LocalDate to);

	@Query("""
		SELECT COALESCE(MAX(b.rangNumero), 0) FROM Bordereau b
		WHERE b.anneeNumero = :annee AND b.statut = :statut
		""")
	int findMaxRangForAnneeAndStatut(@Param("annee") int annee, @Param("statut") BordereauStatut statut);

	@Query("select b.nombreBoites from Bordereau b where b.id = :id")
	Integer findNombreBoitesById(@Param("id") Long id);

	@Query("""
		select b.agent.userName, count(b)
		from Bordereau b
		group by b.agent.id, b.agent.userName
		order by count(b) desc
		""")
	List<Object[]> countBordereauxGroupByAgent(Pageable pageable);

	@Query("""
		select year(b.dateTransfert), month(b.dateTransfert), count(b)
		from Bordereau b
		where b.dateTransfert >= :from and b.dateTransfert <= :to
		group by year(b.dateTransfert), month(b.dateTransfert)
		order by year(b.dateTransfert), month(b.dateTransfert)
		""")
	List<Object[]> countByMonthBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

	long countByAgentIdAndStatut(@Param("agentId") String agentId, @Param("statut") BordereauStatut statut);

	@Query("""
		select year(b.dateTransfert), month(b.dateTransfert), count(b)
		from Bordereau b
		where b.agent.id = :agentId
		  and b.dateTransfert >= :from and b.dateTransfert <= :to
		group by year(b.dateTransfert), month(b.dateTransfert)
		order by year(b.dateTransfert), month(b.dateTransfert)
		""")
	List<Object[]> countByMonthBetweenForAgent(
		@Param("agentId") String agentId,
		@Param("from") LocalDate from,
		@Param("to") LocalDate to
	);

	long countByAgent_IdAndStatutAndNotifAgentVue(
		String agentId,
		BordereauStatut statut,
		boolean notifAgentVue
	);

	List<Bordereau> findTop25ByAgent_IdAndStatutAndNotifAgentVueOrderByIdDesc(
		String agentId,
		BordereauStatut statut,
		boolean notifAgentVue
	);

	@Modifying(clearAutomatically = true)
	@Query("""
		update Bordereau b set b.notifAgentVue = true
		where b.agent.id = :agentId and b.statut = :statut and b.notifAgentVue = false
		""")
	int markAllNotifAgentVue(@Param("agentId") String agentId, @Param("statut") BordereauStatut statut);
}
