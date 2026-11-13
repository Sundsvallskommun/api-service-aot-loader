package se.sundsvall.aotloader.service;

import java.time.Clock;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.db.MigrationRunRepository;
import se.sundsvall.aotloader.service.model.RunStatus;

/**
 * Marks the runs that were running when the application last stopped. Their items are taken up by the next run. It is
 * done before the web server takes any request, so that a run started now is never marked.
 */
@Component
public class RunRecovery implements SmartInitializingSingleton {

	private static final Logger LOG = LoggerFactory.getLogger(RunRecovery.class);

	private final MigrationRunRepository runRepository;
	private final Clock clock;

	RunRecovery(final MigrationRunRepository runRepository, final Clock clock) {
		this.runRepository = runRepository;
		this.clock = clock;
	}

	@Override
	public void afterSingletonsInstantiated() {
		abortInterruptedRuns();
	}

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
