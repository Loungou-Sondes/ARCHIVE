package ommp.archives.repository;

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
}
