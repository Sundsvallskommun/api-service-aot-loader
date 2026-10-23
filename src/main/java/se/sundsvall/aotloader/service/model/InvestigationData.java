package se.sundsvall.aotloader.service.model;

import java.time.LocalDateTime;

/**
 * The investigation an inspection errand, an inspection visit or a permit gets in SupportManagement, which tells when
 * the inspection or the trial of the permit was made (decisions 41 and 46), with the texts of the errand in the source
 * (decisions 44 and 45).
 *
 * @param title       the heading of the investigation
 * @param startedAt   when the inspection or trial started, as the source dates it, null when the source has no date
 * @param completedAt when it was completed, null when the source has no date to complete it at (decision 59)
 * @param texts       the texts of the investigation, never null
 */
public record InvestigationData(String title, LocalDateTime startedAt, LocalDateTime completedAt, InvestigationTexts texts) {

	public InvestigationData {
		texts = texts == null ? InvestigationTexts.NONE : texts;
	}

}
