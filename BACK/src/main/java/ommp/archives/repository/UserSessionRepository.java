package ommp.archives.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import ommp.archives.entity.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

	Optional<UserSession> findByTokenHashAndRevokedFalse(String tokenHash);

	List<UserSession> findByUserNameAndRevokedFalse(String userName);
}
