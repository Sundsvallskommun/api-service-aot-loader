package se.sundsvall.aotloader.integration.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationItemEntity;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;

public interface MigrationItemRepository extends JpaRepository<MigrationItemEntity, Long> {

	Optional<MigrationItemEntity> findBySourceAndKindAndSourceId(Source source, Kind kind, String sourceId);

	/**
	 * @return the row of the item in the status table, empty when it has none
	 */
	default Optional<MigrationItemEntity> findByKey(final ItemKey key) {
		return findBySourceAndKindAndSourceId(key.source(), key.kind(), key.sourceId());
	}

	List<MigrationItemEntity> findAllByOrderBySourceAscKindAscSourceIdAsc();

	/**
	 * @return the item whose errand the migration related to its permit errand with the relation
	 */
	Optional<MigrationItemEntity> findByRelationId(String relationId);
}
