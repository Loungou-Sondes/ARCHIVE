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
		select count(u)
		from UserDetail u
		where u.registrationNumber is not null
		and trim(u.registrationNumber) <> ''
		""")
	long countRegisteredAgents();

	@Query("""
		select count(u)
		from UserDetail u
		where u.statusId = 0
		""")
	long countInactiveAgents();

	@Query(
		value = """
			SELECT COUNT(*)
			FROM USERS a
			LEFT JOIN USER_DETAILS u
			  ON LOWER(TRIM(u.REGISTRATION_NUMBER)) = LOWER(TRIM(a.USER_REGISTRATION_NUMBER))
			WHERE u.STATUS_ID IS NULL OR u.STATUS_ID <> 0
			""",
		nativeQuery = true
	)
	long countActiveAgents();
}
