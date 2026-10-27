package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import se.sundsvall.aotloader.service.model.DecisionData;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNPARSEABLE;

/**
 * Builds the decision a permit was granted by, which the errand of the permit gets as a completed manual decision, the
 * same way for both sources (decision 70).
 */
public final class PermitDecisions {

	private PermitDecisions() {}

	/**
	 * The decision as the source has it. What the decision lacks, the permit gives (see {@link PermitDays}).
	 *
	 * @param  decision the decision the permit was granted by, null when the source has none
	 * @param  typeText the text of the type of the decision, null when unknown
	 * @param  maker    the name of who made the decision, or the code the source names them with; null when it names
	 *                  nobody, and the decision then says that the source lacks it
	 * @param  days     the days the permit gives the decision
	 * @param  terms    the conditions of the permit
	 * @param  files    the files of the permit, among which the document and attachment of the decision are looked for
	 * @param  source   the source
	 * @return          the decision; null when the permit has none, or when neither the decision nor the permit tells
	 *                  when it was made, which SupportManagement requires
	 */
	public static DecisionData of(final SourceDecision decision, final String typeText, final String maker, final PermitDays days, final List<String> terms, final FileList files,
		final Source source) {
		if (decision == null) {
			return null;
		}
		// A time after the migration is no time of the decision, which is then the day the permit gives (G9, see futureDateHit)
		final var decidedAt = Optional.ofNullable(decision.beslutad()).filter(at -> !at.toLocalDate().isAfter(days.today()))
			.or(() -> Optional.ofNullable(days.decidedOn()).map(LocalDate::atStartOfDay)).orElse(null);
		if (decidedAt == null) {
			return null;
		}
		final var documents = Stream.of(files.findOfHolder(decision.worddokument(), DOCUMENTS), files.findOfHolder(decision.bifogadfil(), ATTACHMENTS))
			.flatMap(Optional::stream)
			.toList();
		return new DecisionData(
			Optional.ofNullable(Texts.trimToNull(maker)).orElse(missingIn(source)),
			decidedAt,
			Texts.trimToNull(typeText),
			decision.beslutsText(),
			Optional.ofNullable(decision.giltigtFrom()).orElse(days.issued()),
			Optional.ofNullable(decision.giltigtTom()).orElse(days.validTo()),
			terms,
			documents);
	}

	/**
	 * The decision of a permit whose source has no decision that granted it, built from the permit (decision 75): made
	 * the day the permit gives a decision without a time (see {@link PermitDays}), by a decision maker the source does
	 * not name, valid as the permit and with its conditions as terms. Its description says so, and it links no documents.
	 *
	 * @param  days   the days the permit gives the decision
	 * @param  terms  the conditions of the permit
	 * @param  source the source
	 * @return        the decision; null when the permit gives it no day, which SupportManagement requires
	 */
	public static DecisionData fromPermit(final PermitDays days, final List<String> terms, final Source source) {
		if (days.decidedOn() == null) {
			return null;
		}
		return new DecisionData(missingIn(source), days.decidedOn().atStartOfDay(), "Beslutet finns inte i %s och bygger på tillståndet".formatted(source.getDisplayName()), null,
			days.issued(), days.validTo(), terms, List.of());
	}

	/**
	 * G9 for a decision whose time is after the day of the migration, as a mistyped year may be: the decision is then given
	 * the day the permit gives instead (see {@link #of}).
	 *
	 * @param  decision the decision the permit was granted by, null when the source has none
	 * @param  today    the day of the migration
	 * @return          the hit, empty when the decision has no such time
	 */
	public static Optional<RuleHit> futureDateHit(final SourceDecision decision, final LocalDate today) {
		return Optional.ofNullable(decision)
			.filter(row -> row.beslutad() != null && row.beslutad().toLocalDate().isAfter(today))
			.map(row -> RuleHit.of(UNPARSEABLE, "beslutet %d har datumet %s, efter migreringsdagen".formatted(row.beslutsId(), SourceValues.format(row.beslutad().toLocalDate()))));
	}

	private static String missingIn(final Source source) {
		return "Uppgift saknas i " + source.getDisplayName();
	}

	/**
	 * The days a permit gives the decision it was granted by, where the decision has none of its own.
	 *
	 * @param decidedOn the day that stands in for the time of the decision, null when the permit gives none
	 * @param issued    the day the permit was issued, which is the first day of the decision; null when unknown
	 * @param validTo   the last day of the permit, which is the last day of the decision; null when it is valid until
	 *                  further notice
	 * @param today     the day of the migration, after which no decision is made
	 */
	public record PermitDays(LocalDate decidedOn, LocalDate issued, LocalDate validTo, LocalDate today) {

		/**
		 * A decision is not made after the migration: the day the permit was issued stands in for the time of the decision,
		 * or, when that is later than today, the day the errand of the decision was closed. When neither is known and on or
		 * before today, the decision has no day.
		 *
		 * @param  issued       the day the permit was issued, null when unknown
		 * @param  validTo      the last day of the permit, null when it is valid until further notice
		 * @param  errandClosed the day the errand of the decision was closed, null when unknown or when the decision has
		 *                      no errand
		 * @param  today        the day of the migration
		 * @return              the days
		 */
		public static PermitDays of(final LocalDate issued, final LocalDate validTo, final LocalDate errandClosed, final LocalDate today) {
			final var decidedOn = Stream.of(issued, errandClosed)
				.filter(Objects::nonNull)
				.filter(day -> !day.isAfter(today))
				.findFirst()
				.orElse(null);
			return new PermitDays(decidedOn, issued, validTo, today);
		}
	}
}
