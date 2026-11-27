package se.sundsvall.aotloader.service;

import java.util.ArrayList;
import java.util.List;
import se.sundsvall.aotloader.service.model.RunStatus;

/**
 * How far a run has come: its status and its messages. A run that failed stays FAILED when it is also stopped.
 */
class RunProgress {

	private final List<String> messages = new ArrayList<>();
	private RunStatus status = RunStatus.COMPLETED;
	private int unsavedLines;

	void note(final String message) {
		messages.add(message);
	}

	/**
	 * Counts a row of the report that could not be saved, which the run then tells.
	 */
	void lineNotSaved() {
		unsavedLines++;
	}

	int unsavedLines() {
		return unsavedLines;
	}

	void fail(final String message) {
		messages.add(message);
		status = RunStatus.FAILED;
	}

	void stop() {
		messages.add("Körningen stoppades på begäran");
		if (status == RunStatus.COMPLETED) {
			status = RunStatus.STOPPED;
		}
	}

	RunStatus status() {
		return status;
	}

	List<String> messages() {
		return List.copyOf(messages);
	}
}
