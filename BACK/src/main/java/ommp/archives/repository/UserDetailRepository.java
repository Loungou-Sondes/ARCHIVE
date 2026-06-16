package ommp.archives.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ommp.archives.entity.UserDetail;

public interface UserDetailRepository extends JpaRepository<UserDetail, String> {

	Optional<UserDetail> findByRegistrationNumber(String registrationNumber);

	@Query("""
		SELECT u FROM UserDetail u
		WHERE lower(trim(u.registrationNumber)) = lower(trim(:registrationNumber))
		""")
	Optional<UserDetail> findByRegistrationNumberNormalized(@Param("registrationNumber") String registrationNumber);

	@Query("""
		SELECT u FROM UserDetail u
		WHERE lower(trim(u.registrationNumber)) IN :registrationNumbers
		""")
	List<UserDetail> findByRegistrationNumberNormalizedIn(
		@Param("registrationNumbers") Collection<String> registrationNumbers
	);

	@Query("""
		select lower(trim(u.registrationNumber))
		from UserDetail u
		where u.registrationNumber is not null
		  and length(trim(u.registrationNumber)) > 0
		  and (
		    lower(u.firstName) like :like
		    or lower(u.lastName) like :like
		    or lower(coalesce(u.directionId, '')) like :like
		  )
		""")
	List<String> findRegistrationNumbersBySearchLike(@Param("like") String like);

	@Query("""
		select count(u)
		from UserDetail u
		where u.registrationNumber is not null
		  and length(trim(u.registrationNumber)) > 0
		""")
	long countRegisteredAgents();
}
