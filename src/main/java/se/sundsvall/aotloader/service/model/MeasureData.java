package se.sundsvall.aotloader.service.model;

import java.time.LocalDateTime;

/**
 * A measure the errand gets in SupportManagement: a remark of an inspection visit, as the AoT solution keeps the
 * deficiencies of an inspection (decision 57). Its description is free text that may name persons, so it is never
 * logged or stored.
 *
 * @param title       the remark, the text of its code
 * @param description the note of the remark, null when it has none
 * @param completedAt when the visit that made the remark was, without time zone; null when the visit has no date
 */
public record MeasureData(String title, String description, LocalDateTime completedAt) {

	@Override
	public String toString() {
		return "MeasureData[title=" + title + ", description=" + (description != null) + ", completedAt=" + completedAt + "]";
	}
}
