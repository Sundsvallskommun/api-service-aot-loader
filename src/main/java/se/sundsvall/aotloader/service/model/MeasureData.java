package se.sundsvall.aotloader.service.model;

/**
 * A measure the errand gets in SupportManagement: a remark of an inspection visit, as the AoT solution keeps the
 * deficiencies of an inspection (decision 57). Its description is free text that may name persons, so it is never
 * logged or stored.
 *
 * @param title       the remark, the text of its code
 * @param description the note of the remark, null when it has none
 */
public record MeasureData(String title, String description) {

	@Override
	public String toString() {
		return "MeasureData[title=" + title + ", description=" + (description != null) + "]";
	}
}
