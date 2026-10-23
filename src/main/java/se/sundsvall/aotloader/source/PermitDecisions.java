package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import se.sundsvall.aotloader.service.model.DecisionData;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;

/**
 * Builds the decision a permit was granted by, which the errand of the permit gets as a completed manual decision, the
 * same way for both sources (decision 70).
 */
public final class PermitDecisions {

	private PermitDecisions() {}

	/**
	 * The decision as the source has it. What the decision lacks, the permit gives: the day the permit was issued stands
	 * in for the time of the decision and its first day, and the last day of the permit for its last day.
	 *
	 * @param  decision the decision the permit was granted by, null when the source has none
	 * @param  typeText the text of the type of the decision, null when unknown
	 * @param  maker    the name of who made the decision, or the code the source names them with; null when it names
	 *                  nobody, and the decision then says that the source lacks it
	 * @param  issued   the day the permit was issued, null when unknown
	 * @param  validTo  the last day of the permit, null when it is valid until further notice
	 * @param  terms    the conditions of the permit
	 * @param  files    the files of the permit, among which the document and attachment of the decision are looked for
	 * @param  source   the source
	 * @return          the decision; null when the permit has none, or when neither the decision nor the permit tells
	 *                  when it was made, which SupportManagement requires
	 */
	public static DecisionData of(final SourceDecision decision, final String typeText, final String maker, final LocalDate issued, final LocalDate validTo, final List<String> terms,
		final FileList files, final Source source) {
		if (decision == null) {
			return null;
		}
		final var decidedAt = Optional.ofNullable(decision.beslutad()).or(() -> Optional.ofNullable(issued).map(LocalDate::atStartOfDay)).orElse(null);
		if (decidedAt == null) {
			return null;
		}
		final var documents = Stream.of(files.findOfHolder(decision.worddokument(), DOCUMENTS), files.findOfHolder(decision.bifogadfil(), ATTACHMENTS))
			.flatMap(Optional::stream)
			.toList();
		return new DecisionData(
			Optional.ofNullable(Texts.trimToNull(maker)).orElse("Uppgift saknas i " + source.getDisplayName()),
			decidedAt,
			Texts.trimToNull(typeText),
			decision.beslutsText(),
			Optional.ofNullable(decision.giltigtFrom()).orElse(issued),
			Optional.ofNullable(decision.giltigtTom()).orElse(validTo),
			terms,
			documents);
	}
}
