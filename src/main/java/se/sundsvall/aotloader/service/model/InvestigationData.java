package se.sundsvall.aotloader.service.model;

import java.time.LocalDateTime;

/**
 * The investigation an inspection errand gets in SupportManagement, which tells when the inspection was made (decision
 * 41).
 *
 * @param title       the heading of the investigation
 * @param startedAt   when the inspection started, as the source dates it, null when the source has no date
 * @param completedAt when the inspection was completed, null when the source has no end
 */
public record InvestigationData(String title, LocalDateTime startedAt, LocalDateTime completedAt) {

	/**
	 * @return true when the source tells when the inspection was completed
	 */
	public boolean completed() {
		return completedAt != null;
	}
}
