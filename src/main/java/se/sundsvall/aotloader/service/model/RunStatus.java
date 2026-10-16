package se.sundsvall.aotloader.service.model;

/**
 * The status of a run.
 */
public enum RunStatus {

	RUNNING,
	COMPLETED,
	/** Stopped on request, between two items. */
	STOPPED,
	/** Could not start or could not read a source. */
	FAILED,
	/** Was running when the application stopped. */
	ABORTED
}
