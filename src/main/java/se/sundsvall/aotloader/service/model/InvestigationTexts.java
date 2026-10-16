package se.sundsvall.aotloader.service.model;

/**
 * The texts of an investigation in SupportManagement, taken from the source (decisions 44–46). They are free text that
 * may name persons, so they are never logged or stored.
 *
 * @param description the notes of the errand, null when there are none
 * @param summary     what was done: the events with a text, one line each in order of time, or the note of a visit;
 *                    null when there is nothing
 * @param conclusion  the outcome: the decisions, one line each in order of time, or the remarks of a visit; null when
 *                    there is none
 */
public record InvestigationTexts(String description, String summary, String conclusion) {

	/**
	 * No texts at all.
	 */
	public static final InvestigationTexts NONE = new InvestigationTexts(null, null, null);

	@Override
	public String toString() {
		return "InvestigationTexts[description=" + (description != null) + ", summary=" + (summary != null) + ", conclusion=" + (conclusion != null) + "]";
	}
}
