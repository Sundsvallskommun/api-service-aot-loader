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
 * @param title              the heading of the investigation
 * @param startedAt          when the inspection or trial started, as the source dates it, null when the source has no
 *                           date
 * @param completedAt        when it was completed, null when the source has no date to complete it at (decision 59)
 * @param texts              the texts of the investigation, never null
 * @param protocol           the inspection protocol Draken shows the investigation of an inspection errand as, by the
 *                           key of its template, null for none (decision 77)
 * @param protocolParameters the fields of the protocol, by the keys of the template
 * @param sections           the sections of the investigation, which Draken shows the examinations of a permit in
 */
public record InvestigationData(String title, LocalDateTime startedAt, LocalDateTime completedAt, InvestigationTexts texts, String protocol, List<Parameter> protocolParameters,
	List<Section> sections) {

	public InvestigationData {
		texts = texts == null ? InvestigationTexts.NONE : texts;
		protocolParameters = protocolParameters == null ? List.of() : List.copyOf(protocolParameters);
		sections = sections == null ? List.of() : List.copyOf(sections);
	}

	/**
	 * An investigation without an inspection protocol and without sections.
	 */
	public InvestigationData(final String title, final LocalDateTime startedAt, final LocalDateTime completedAt, final InvestigationTexts texts) {
		this(title, startedAt, completedAt, texts, null, List.of(), List.of());
	}

	/**
	 * @return the investigation as the inspection protocol of the template, with these fields
	 */
	public InvestigationData withProtocol(final String template, final List<Parameter> parameters) {
		return new InvestigationData(title, startedAt, completedAt, texts, template, parameters, sections);
	}

	/**
	 * @return the investigation with this section besides those it has
	 */
	public InvestigationData withSection(final Section section) {
		return new InvestigationData(title, startedAt, completedAt, texts, protocol, protocolParameters, Stream.concat(sections.stream(), Stream.of(section))
			.toList());
	}

	@Override
	public String toString() {
		return "InvestigationData[startedAt=" + startedAt + ", completedAt=" + completedAt + ", texts=" + texts + ", protocol=" + protocol + ", protocolParameters=" + protocolParameters.size()
			+ ", sections=" + sections.stream().map(Section::key).toList() + "]";
	}

	/**
	 * A section of the investigation, as Draken writes the sections of the investigation of a permit, with the parameters
	 * Draken reads in it.
	 *
	 * @param key        the key of the section, by which Draken shows it
	 * @param heading    its heading, as Draken heads it
	 * @param sortOrder  its place among the sections, as Draken orders them
	 * @param parameters its parameters, by the keys and with the labels Draken gives them
	 */
	public record Section(String key, String heading, int sortOrder, List<Parameter> parameters) {

		public Section {
			requireNonNull(key, "key");
			parameters = parameters == null ? List.of() : List.copyOf(parameters);
		}
	}
}
