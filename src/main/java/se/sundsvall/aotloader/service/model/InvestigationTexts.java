package se.sundsvall.aotloader.service.model;

import java.util.Arrays;
import java.util.List;

/**
 * The texts of an investigation in SupportManagement, taken from the source (decisions 44–46): parts with a heading
 * each, such as the notes, the events and the decisions of an errand. They are free text that may name persons, so they
 * are never logged or stored.
 *
 * @param parts the parts that have a text, in order; a part without one is left out
 */
public record InvestigationTexts(List<Part> parts) {

	/**
	 * No texts at all.
	 */
	public static final InvestigationTexts NONE = new InvestigationTexts(List.of());

	public InvestigationTexts {
		parts = parts == null ? List.of() : parts.stream().filter(part -> part.text() != null && !part.text().isBlank()).toList();
	}

	/**
	 * @return the texts of the parts that have a text, in the order given
	 */
	public static InvestigationTexts of(final Part... parts) {
		return new InvestigationTexts(Arrays.asList(parts));
	}

	@Override
	public String toString() {
		return "InvestigationTexts[parts=" + parts + "]";
	}

	/**
	 * A part of the texts.
	 *
	 * @param heading what the part is, e.g. "Händelser"
	 * @param text    the text, its lines separated by line breaks; null when there is none
	 */
	public record Part(String heading, String text) {

		@Override
		public String toString() {
			return "Part[heading=" + heading + ", text=" + (text != null) + "]";
		}
	}
}
