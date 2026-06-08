package ommp.archives.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ommp.archives.entity.BoiteEtat;
import ommp.archives.entity.BoiteEtatType;

public interface BoiteEtatRepository extends JpaRepository<BoiteEtat, Long> {

	@Modifying
	@Query("delete from BoiteEtat e where e.boite.id = :boiteId")
	void deleteByBoiteId(@Param("boiteId") Long boiteId);

	@Query(
		"select min(e.dateEtat) from BoiteEtat e where e.boite.id = :boiteId and e.typeEtat = :type"
	)
	Optional<LocalDate> findEarliestDateByBoiteIdAndType(
		@Param("boiteId") Long boiteId,
		@Param("type") BoiteEtatType type
	);
}
