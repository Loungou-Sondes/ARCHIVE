package ommp.archives.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import ommp.archives.entity.UserAccount;

public interface UserAccountRepository extends JpaRepository<UserAccount, String>, JpaSpecificationExecutor<UserAccount> {

	Optional<UserAccount> findByUserName(String userName);

	@Query("SELECT u FROM UserAccount u WHERE LOWER(u.userName) = LOWER(:userName)")
	Optional<UserAccount> findByUserNameIgnoreCase(@Param("userName") String userName);

	long countByPasswordResetRequestedTrue();

	/**
	 * Projection sans charger PASSWORD ni autres colonnes (Oracle / grosses lignes).
	 */
	@Query(
		"SELECT u.id, u.userName, u.email, u.gender, u.phoneNumber, u.role, u.userRegistrationNumber "
			+ "FROM UserAccount u ORDER BY u.userName ASC")
	List<Object[]> findAllUsersListingScalars();

	@Query("""
		select u.id
		from UserAccount u
		where u.userRegistrationNumber is not null
		  and length(trim(u.userRegistrationNumber)) > 0
		  and lower(trim(u.userRegistrationNumber)) in :regs
		""")
	List<String> findIdsByRegistrationNormalizedIn(@Param("regs") Collection<String> regs);

	@Query("""
		select count(a)
		from UserAccount a
		where (a.role is null or upper(a.role) not like '%ADMIN%')
		  and not exists (
		    select 1 from UserDetail d
		    where lower(trim(d.registrationNumber)) = lower(trim(a.userRegistrationNumber))
		      and d.statusId = 0
		  )
		""")
	long countActiveManagedAgents();

	@Query("""
		select count(a)
		from UserAccount a
		where (a.role is null or upper(a.role) not like '%ADMIN%')
		  and exists (
		    select 1 from UserDetail d
		    where lower(trim(d.registrationNumber)) = lower(trim(a.userRegistrationNumber))
		      and d.statusId = 0
		  )
		""")
	long countInactiveManagedAgents();
}
