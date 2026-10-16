package se.sundsvall.aotloader.integration.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationItemEntity;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;

public interface MigrationItemRepository extends JpaRepository<MigrationItemEntity, Long> {

	Optional<MigrationItemEntity> findBySourceAndKindAndSourceId(Source source, Kind kind, String sourceId);

	List<MigrationItemEntity> findAllByOrderBySourceAscKindAscSourceIdAsc();
}
