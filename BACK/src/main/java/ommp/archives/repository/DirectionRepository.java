package ommp.archives.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import ommp.archives.entity.Direction;

public interface DirectionRepository extends JpaRepository<Direction, String> {

	List<Direction> findAllByOrderByIdAsc();
}
