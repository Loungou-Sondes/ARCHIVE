package ommp.archives.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import ommp.archives.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	Page<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
