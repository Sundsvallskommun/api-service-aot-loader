package se.sundsvall.aotloader.service.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * The investigation an inspection errand, an inspection visit or a permit gets in SupportManagement, which tells when
 * the inspection or the trial of the permit was made (decisions 41 and 46), with the texts of the errand in the source
 * (decisions 44 and 45). Its heading, which names the place, and the fields of its protocol may name persons, and
 * {@link #toString()} shows none of them.
 *
 * @param title          the heading of the investigation
 * @param startedAt      when the inspection or trial started, as the source dates it, null when the source has no date
 * @param completedAt    when it was completed, null when the source has no date to complete it at (decision 59)
 * @param texts          the texts of the investigation, never null
 * @param protocol       the inspection protocol Draken shows the investigation of an inspection errand as, by the key
 *                       of its template, null for none (decision 77)
 * @param protocolFields the fields of the protocol, the JSON parameter of the investigation Draken reads them in by the
 *                       keys of the template (decision 92), null for an investigation without a protocol
 * @param sections       the sections of the investigation, which Draken shows the examinations of a permit in
 */
public record InvestigationData(String title, LocalDateTime startedAt, LocalDateTime completedAt, InvestigationTexts texts, String protocol, JsonContent protocolFields,
	List<Section> sections) {

	public InvestigationData {
		texts = texts == null ? InvestigationTexts.NONE : texts;
		sections = sections == null ? List.of() : List.copyOf(sections);
	}

	/**
	 * An investigation without an inspection protocol and without sections.
	 */
	public InvestigationData(final String title, final LocalDateTime startedAt, final LocalDateTime completedAt, final InvestigationTexts texts) {
		this(title, startedAt, completedAt, texts, null, null, List.of());
	}

	/**
	 * @return the investigation as the inspection protocol of the template, with these fields
	 */
	public InvestigationData withProtocol(final String template, final JsonContent fields) {
		return new InvestigationData(title, startedAt, completedAt, texts, template, fields, sections);
	}

	/**
	 * @return the investigation with this section besides those it has
	 */
	public InvestigationData withSection(final Section section) {
		return new InvestigationData(title, startedAt, completedAt, texts, protocol, protocolFields, Stream.concat(sections.stream(), Stream.of(section))
			.toList());
	}

	@Override
	public String toString() {
		return "InvestigationData[startedAt=" + startedAt + ", completedAt=" + completedAt + ", texts=" + texts + ", protocol=" + protocol + ", protocolFields=" + protocolFields
			+ ", sections=" + sections.stream().map(Section::key).toList() + "]";
	}

	/**
	 * A section of the investigation, as Draken writes the sections of the investigation of a permit, with what Draken
	 * reads in it. Its text may name persons, and {@link #toString()} shows neither it nor its JSON parameter.
	 *
	 * @param key       the key of the section, by which Draken shows it
	 * @param heading   its heading, as Draken heads it
	 * @param sortOrder its place among the sections, as Draken orders them
	 * @param text      what the source says in the section, as lines of plain text, which Draken shows on a migrated
	 *                  errand (decision 96); null for none
	 * @param content   what the section establishes, its JSON parameter as Draken reads it (decision 92)
	 */
	public record Section(String key, String heading, int sortOrder, String text, JsonContent content) {

		public Section {
			requireNonNull(key, "key");
			requireNonNull(content, "content");
		}

		@Override
		public String toString() {
			return "Section[key=" + key + ", sortOrder=" + sortOrder + "]";
		}
	}
}
