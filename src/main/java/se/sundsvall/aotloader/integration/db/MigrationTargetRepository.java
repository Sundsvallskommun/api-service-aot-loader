package se.sundsvall.aotloader.integration.db;

import org.springframework.data.jpa.repository.JpaRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationTargetEntity;

public interface MigrationTargetRepository extends JpaRepository<MigrationTargetEntity, Integer> {
}
