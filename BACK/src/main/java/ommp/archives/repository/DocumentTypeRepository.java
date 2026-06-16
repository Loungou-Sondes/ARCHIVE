package ommp.archives.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ommp.archives.dto.documenttype.DocumentTypeResponse;
import ommp.archives.entity.DocumentType;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long>, JpaSpecificationExecutor<DocumentType> {

	@Query("select dt from DocumentType dt where lower(trim(dt.title)) = lower(trim(:title))")
	Optional<DocumentType> findByTitleIgnoreCase(@Param("title") String title);

	@Query("""
		select new ommp.archives.dto.documenttype.DocumentTypeResponse(
		  dt.id, dt.title, d.id, d.label
		)
		from DocumentType dt
		left join dt.direction d
		order by lower(dt.title) asc
		""")
	List<DocumentTypeResponse> findAllOrderedForOptions();

	boolean existsByTitleIgnoreCase(String title);

	boolean existsByTitleIgnoreCaseAndIdNot(String title, Long id);
}
