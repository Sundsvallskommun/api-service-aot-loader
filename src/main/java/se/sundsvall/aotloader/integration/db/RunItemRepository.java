package se.sundsvall.aotloader.integration.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import se.sundsvall.aotloader.integration.db.model.RunItemEntity;

public interface RunItemRepository extends JpaRepository<RunItemEntity, Long> {

	List<RunItemEntity> findByRunIdOrderById(String runId);
}
