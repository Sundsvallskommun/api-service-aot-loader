package se.sundsvall.aotloader.integration.db;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import se.sundsvall.aotloader.integration.db.model.ReviewExceptionEntity;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;

public interface ReviewExceptionRepository extends JpaRepository<ReviewExceptionEntity, Long> {

	Optional<ReviewExceptionEntity> findBySourceAndKindAndSourceId(Source source, Kind kind, String sourceId);

	List<ReviewExceptionEntity> findAllByOrderBySourceAscKindAscSourceIdAsc();
}
