package se.sundsvall.aotloader.integration.db;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.sundsvall.aotloader.integration.db.model.RunItemEntity;
import se.sundsvall.aotloader.service.model.Outcome;

public interface RunItemRepository extends JpaRepository<RunItemEntity, Long> {

	List<RunItemEntity> findByRunIdOrderById(String runId);

	List<RunItemEntity> findByRunIdAndOutcomeInOrderById(String runId, Collection<Outcome> outcomes);

	/**
	 * @return the number of rows of the run per outcome, without reading the rows
	 */
	@Query("SELECT r.outcome AS outcome, COUNT(r) AS amount FROM RunItemEntity r WHERE r.runId = :runId GROUP BY r.outcome")
	List<OutcomeCount> countOutcomes(@Param("runId") String runId);

	/**
	 * The number of rows of a report with an outcome.
	 */
	interface OutcomeCount {

		Outcome getOutcome();

		long getAmount();
	}
}
