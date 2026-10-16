package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.db.MigrationRunRepository;
import se.sundsvall.aotloader.service.model.RunStatus;

/**
 * Marks the runs that were running when the application last stopped. Their items are taken up by the next run.
 */
@Component
public class RunRecovery {

	private static final Logger LOG = LoggerFactory.getLogger(RunRecovery.class);

	private final MigrationRunRepository runRepository;
	private final Clock clock;

	RunRecovery(final MigrationRunRepository runRepository, final Clock clock) {
		this.runRepository = runRepository;
		this.clock = clock;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void abortInterruptedRuns() {
		runRepository.findByStatus(RunStatus.RUNNING).forEach(run -> {
			run.setStatus(RunStatus.ABORTED);
			run.setFinished(OffsetDateTime.now(clock));
			run.setMessage("Avbruten: applikationen stoppades medan körningen pågick. Nästa körning fortsätter där varje ärende stannade.");
			runRepository.save(run);
			LOG.warn("Run {} was running when the application stopped and is marked ABORTED", run.getId());
		});
	}
}
