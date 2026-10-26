package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * What on a place was a former holder's rather than its current owner's: the registrations of a notification (G31,
 * decision 62), and the inspection errands and events that are not migrated (G32, decision 72). An errand whose
 * applicant has another number than the owner is a former holder's. When the place has one, what came before the
 * first errand of the owner is a former holder's too, or, when the owner has none, what came up to the
 * day the last errand of a former holder closed. An errand whose applicant is the owner is never a former holder's, and
 * a place without an errand of a former holder has nothing of one.
 *
 * @param errandIds the ArendeID of the errands of former holders
 * @param until     the last day of the former holders, null when the place has nothing of one
 */
public record FormerHolders(Set<Integer> errandIds, LocalDate until) {

	public FormerHolders {
		errandIds = Set.copyOf(errandIds);
	}

	/**
	 * What was a former holder's of the inspection errands and the days of the place (G32). An applicant whose number
	 * cannot be normalized, as one written in part, is unknown and not a former holder (section 7.7.1), so that what may
	 * be the current holder's is migrated.
	 *
	 * @param  errands    the errands of the place, all of them or those of one kind
	 * @param  applicants the applicants of the errands, by SokandeID
	 * @param  owner      the current owner of the place, null when it has none
	 * @param  today      the day the century of a personal identity number is chosen from
	 * @return            what of the errands and the days of the place was a former holder's
	 */
	public static FormerHolders of(final List<? extends SourceErrand> errands, final Map<Integer, ContactRow> applicants, final ContactRow owner, final LocalDate today) {
		return of(errands, applicants, owner, (applicant, holder) -> PartyRows.otherHolder(applicant, holder, today), today);
	}

	/**
	 * What was a former holder's of the registrations of a notification (G31, decision 62). An applicant whose number
	 * cannot be normalized is a former holder when its digits differ from the owner's, since the files of its
	 * registrations may name another holder and would otherwise go on the post the current owner sees, and a notification
	 * of only such registrations is reviewed (G19).
	 *
	 * @param  errands    the errands of the product on the place
	 * @param  applicants the applicants of the errands, by SokandeID
	 * @param  owner      the current owner of the place, null when it has none
	 * @param  today      the day the century of a personal identity number is chosen from
	 * @return            what of the errands and the days of the place was a former holder's
	 */
	public static FormerHolders ofRegistrations(final List<? extends SourceErrand> errands, final Map<Integer, ContactRow> applicants, final ContactRow owner, final LocalDate today) {
		return of(errands, applicants, owner, (applicant, holder) -> PartyRows.mayBeOtherHolder(applicant, holder, today), today);
	}

	private static FormerHolders of(final List<? extends SourceErrand> errands, final Map<Integer, ContactRow> applicants, final ContactRow owner,
		final BiPredicate<ContactRow, ContactRow> otherHolder, final LocalDate today) {
		final Predicate<SourceErrand> ofOwner = errand -> PartyRows.sameHolder(applicantOf(errand, applicants), owner, today);
		final Predicate<SourceErrand> ofFormer = errand -> otherHolder.test(applicantOf(errand, applicants), owner);
		final var formerEnd = errands.stream()
			.filter(ofFormer)
			.map(errand -> Optional.ofNullable(errand.avslutad()).orElse(errand.openedOrRegistered()))
			.filter(Objects::nonNull)
			.max(Comparator.naturalOrder());
		final var ownerStart = errands.stream()
			.filter(ofOwner)
			.map(SourceErrand::openedOrRegistered)
			.filter(Objects::nonNull)
			.min(Comparator.naturalOrder());
		// The owner's first errand bounds the former holders also when none of their errands has a date
		final var until = errands.stream().anyMatch(ofFormer) ? ownerStart.map(start -> start.minusDays(1)).or(() -> formerEnd).orElse(null) : null;
		return new FormerHolders(errands.stream()
			.filter(errand -> ofFormer.test(errand) || (!ofOwner.test(errand) && isUpTo(errand.openedOrRegistered(), until)))
			.map(SourceErrand::arendeId)
			.collect(toUnmodifiableSet()), until);
	}

	/**
	 * @return true for a day of the former holders, on or before their last day; false for an unknown day
	 */
	public boolean isUpTo(final LocalDate day) {
		return isUpTo(day, until);
	}

	private static boolean isUpTo(final LocalDate day, final LocalDate until) {
		return day != null && until != null && !day.isAfter(until);
	}

	private static ContactRow applicantOf(final SourceErrand errand, final Map<Integer, ContactRow> applicants) {
		return errand.sokandeId() == null ? null : applicants.get(errand.sokandeId());
	}
}
