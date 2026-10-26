package se.sundsvall.aotloader.service.model;

import java.time.LocalDateTime;
import java.util.List;

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
 */
public record InvestigationData(String title, LocalDateTime startedAt, LocalDateTime completedAt, InvestigationTexts texts, String protocol, List<Parameter> protocolParameters) {

	public InvestigationData {
		texts = texts == null ? InvestigationTexts.NONE : texts;
		protocolParameters = protocolParameters == null ? List.of() : List.copyOf(protocolParameters);
	}

	/**
	 * An investigation without an inspection protocol.
	 */
	public InvestigationData(final String title, final LocalDateTime startedAt, final LocalDateTime completedAt, final InvestigationTexts texts) {
		this(title, startedAt, completedAt, texts, null, List.of());
	}

	/**
	 * @return the investigation as the inspection protocol of the template, with these fields
	 */
	public InvestigationData withProtocol(final String template, final List<Parameter> parameters) {
		return new InvestigationData(title, startedAt, completedAt, texts, template, parameters);
	}

	@Override
	public String toString() {
		return "InvestigationData[startedAt=" + startedAt + ", completedAt=" + completedAt + ", texts=" + texts + ", protocol=" + protocol + ", protocolParameters=" + protocolParameters.size() + "]";
	}
}
