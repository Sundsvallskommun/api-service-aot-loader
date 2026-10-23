package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.support.SourceValues;

/**
 * Builds the investigation every permit gets in SupportManagement, the same way for both sources (decision 46).
 */
public final class PermitInvestigations {

	private PermitInvestigations() {}

	/**
	 * The investigation of a permit: it started when the first errand that issued the permit was opened, and was completed
	 * when the permit was decided, or, without a decision date, when the last of those errands was closed. An end before
	 * the start becomes the start, as for the inspections. A permit without an issuing errand gets an investigation all the
	 * same, completed when it was decided and without texts.
	 *
	 * @param  title   the heading of the investigation
	 * @param  opened  the days the errands that issued the permit were opened
	 * @param  closed  the days those errands were closed
	 * @param  decided the day the permit was decided, null when unknown; not the day it is valid from, which may lie ahead
	 * @param  texts   the texts of those errands
	 * @return         the investigation
	 */
	public static InvestigationData of(final String title, final Collection<LocalDate> opened, final Collection<LocalDate> closed, final LocalDate decided,
		final InvestigationTexts texts) {
		final var started = opened.stream().filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
		final var completed = Optional.ofNullable(decided)
			.or(() -> closed.stream().filter(Objects::nonNull).max(LocalDate::compareTo))
			.map(end -> started != null && end.isBefore(started) ? started : end)
			.orElse(null);
		return new InvestigationData(title, SourceValues.startOfDay(started), SourceValues.startOfDay(completed), texts);
	}
}
