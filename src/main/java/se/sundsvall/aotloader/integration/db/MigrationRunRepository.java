package se.sundsvall.aotloader.integration.db;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationRunEntity;
import se.sundsvall.aotloader.service.model.RunStatus;

public interface MigrationRunRepository extends JpaRepository<MigrationRunEntity, String> {

	List<MigrationRunEntity> findByMunicipalityIdOrderByStartedDesc(String municipalityId);

	List<MigrationRunEntity> findByStatus(RunStatus status);
}
