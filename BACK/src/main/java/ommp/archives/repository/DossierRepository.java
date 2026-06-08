package ommp.archives.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ommp.archives.entity.Dossier;

public interface DossierRepository extends JpaRepository<Dossier, Long> {

	List<Dossier> findByBoite_IdOrderByAnneeDescTitreAsc(Long boiteId);

	Optional<Dossier> findByIdAndBoite_Id(Long id, Long boiteId);
}
