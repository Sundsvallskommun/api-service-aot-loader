package se.sundsvall.aotloader.service;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Lets one run at a time hold the migration. The protection against duplicates in the target systems is made in code,
 * so it relies on no other run writing at the same time (section 6).
 */
@Component
public class RunLock {

	private final AtomicReference<String> holder = new AtomicReference<>();

	/**
	 * @return true when the run got the lock, false when another run holds it
	 */
	public boolean tryAcquire(final String runId) {
		return holder.compareAndSet(null, runId);
	}

	/**
	 * Releases the lock if the run holds it.
	 */
	public void release(final String runId) {
		holder.compareAndSet(runId, null);
	}

	/**
	 * @return the run that holds the lock, empty when none does
	 */
	public Optional<String> holder() {
		return Optional.ofNullable(holder.get());
	}
}
