package ommp.archives.repository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ommp.archives.entity.Emplacement;
import ommp.archives.entity.TypeEmp;

public interface EmplacementRepository extends JpaRepository<Emplacement, String> {

	List<Emplacement> findByTypeEmp(TypeEmp typeEmp);

	List<Emplacement> findByTypeEmpOrderByNumeroAsc(TypeEmp typeEmp);

	List<Emplacement> findByTypeEmpAndBoiteIdIsNullOrderByNumeroAsc(TypeEmp typeEmp);

	List<Emplacement> findByBoiteIdAndTypeEmpOrderByNumeroAsc(Long boiteId, TypeEmp typeEmp);

	List<Emplacement> findByBoiteId(Long boiteId);

	List<Emplacement> findByNumeroStartingWith(String prefix);

	long countByTypeEmp(TypeEmp typeEmp);

	long countByTypeEmpAndBoiteIdIsNotNull(TypeEmp typeEmp);

	@Query(
		value = """
			SELECT COUNT(*) FROM (
			  SELECT t.ID
			  FROM EMPLACEMENTS t
			  INNER JOIN EMPLACEMENTS b ON b.TYPE_EMP = 'BLOC'
			    AND LENGTH(b.NUMERO) = LENGTH(t.NUMERO) + 1
			    AND SUBSTR(b.NUMERO, 1, LENGTH(t.NUMERO)) = t.NUMERO
			  WHERE t.TYPE_EMP = 'TABLETTE'
			  GROUP BY t.ID
			  HAVING COUNT(b.ID) > 0
			    AND (SUM(CASE WHEN b.ID_BOITE IS NOT NULL THEN 1 ELSE 0 END) * 100.0 / COUNT(b.ID)) >= :seuil
			)
			""",
		nativeQuery = true
	)
	long countTablettesPresquePleines(@Param("seuil") double seuil);

	@Query(
		value = """
			SELECT
			  t.ID,
			  t.NUMERO,
			  COALESCE(t.METRAGE, 0),
			  COUNT(b.ID),
			  SUM(CASE WHEN b.ID_BOITE IS NOT NULL THEN 1 ELSE 0 END),
			  ROUND(SUM(CASE WHEN b.ID_BOITE IS NOT NULL THEN 1 ELSE 0 END) * 100.0 / COUNT(b.ID))
			FROM EMPLACEMENTS t
			INNER JOIN EMPLACEMENTS b ON b.TYPE_EMP = 'BLOC'
			  AND LENGTH(b.NUMERO) = LENGTH(t.NUMERO) + 1
			  AND SUBSTR(b.NUMERO, 1, LENGTH(t.NUMERO)) = t.NUMERO
			WHERE t.TYPE_EMP = 'TABLETTE'
			GROUP BY t.ID, t.NUMERO, t.METRAGE
			HAVING COUNT(b.ID) > 0
			  AND (SUM(CASE WHEN b.ID_BOITE IS NOT NULL THEN 1 ELSE 0 END) * 100.0 / COUNT(b.ID)) >= :seuil
			ORDER BY 6 DESC, t.NUMERO ASC
			""",
		countQuery = """
			SELECT COUNT(*) FROM (
			  SELECT t.ID
			  FROM EMPLACEMENTS t
			  INNER JOIN EMPLACEMENTS b ON b.TYPE_EMP = 'BLOC'
			    AND LENGTH(b.NUMERO) = LENGTH(t.NUMERO) + 1
			    AND SUBSTR(b.NUMERO, 1, LENGTH(t.NUMERO)) = t.NUMERO
			  WHERE t.TYPE_EMP = 'TABLETTE'
			  GROUP BY t.ID
			  HAVING COUNT(b.ID) > 0
			    AND (SUM(CASE WHEN b.ID_BOITE IS NOT NULL THEN 1 ELSE 0 END) * 100.0 / COUNT(b.ID)) >= :seuil
			)
			""",
		nativeQuery = true
	)
	Page<Object[]> findTablettesPresquePleinesPage(@Param("seuil") double seuil, Pageable pageable);

	@Query("""
		select e from Emplacement e
		where e.typeEmp = ommp.archives.entity.TypeEmp.BLOC
		  and e.boiteId is null
		  and (:q is null or lower(e.numero) like lower(concat('%', :q, '%')))
		""")
	Page<Emplacement> findFreeBlocsPage(@Param("q") String q, Pageable pageable);

	private static int epiOrderingKey(String numero) {
		if (numero == null) {
			return Integer.MAX_VALUE;
		}
		String t = numero.trim();
		try {
			return Integer.parseInt(t);
		} catch (NumberFormatException e) {
			return t.hashCode();
		}
	}

	default List<Emplacement> findEpisRootsOrderByCreatedAtAsc() {
		return findByTypeEmp(TypeEmp.EPI).stream()
			.sorted(Comparator.comparingInt(x -> epiOrderingKey(x.getNumero())))
			.toList();
	}

	default Optional<Emplacement> findLastEpiRoot() {
		return findByTypeEmp(TypeEmp.EPI).stream()
			.max(Comparator.comparingInt(x -> epiOrderingKey(x.getNumero())));
	}
}
