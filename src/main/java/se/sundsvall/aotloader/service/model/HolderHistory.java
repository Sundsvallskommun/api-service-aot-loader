package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What the source tells of the holders of a place before its current permit: its earlier permits and its earlier
 * owners (decision 114). It shows whether an inspection before the day the current permit was issued concerned the
 * holder of that permit. The numbers are compared in memory only.
 *
 * @param holder  the number of the current owner of the place, who holds its permit; null when it cannot be read
 * @param permits the earlier permits of the place, in the order the source gave them
 * @param owners  the earlier owners of the place
 */
public record HolderHistory(String holder, List<EarlierPermit> permits, List<EarlierOwner> owners) {

	public HolderHistory {
		permits = List.copyOf(permits);
		owners = List.copyOf(owners);
	}

	/**
	 * What shows that an inspection concerned the current holder.
	 */
	public enum Source {

		/** The latest earlier permit issued on or before the inspection was the current holder's. */
		EARLIER_PERMIT,
		/** Every other owner of the place had left before the inspection, or it was within a period of the current holder. */
		OWNER_HISTORY
	}

	/**
	 * An earlier permit of the place.
	 *
	 * @param issued the day it was issued, null when unknown
	 * @param holder the number of its holder, null when it cannot be read
	 */
	public record EarlierPermit(LocalDate issued, String holder) {

		@Override
		public String toString() {
			return "EarlierPermit[issued=" + issued + "]";
		}
	}

	/**
	 * An earlier owner of the place.
	 *
	 * @param from   the first day of the owner, null when unknown
	 * @param to     the last day of the owner, null when unknown
	 * @param holder the number of the owner, null when it cannot be read
	 */
	public record EarlierOwner(LocalDate from, LocalDate to, String holder) {

		@Override
		public String toString() {
			return "EarlierOwner[from=" + from + ", to=" + to + "]";
		}
	}

	/**
	 * @param source what shows it
	 * @param date   for an earlier permit the day it was issued; for the earlier owners the last day of the latest other
	 *               owner, null when there is none or the inspection was within a period of the current holder
	 */
	public record Evidence(Source source, LocalDate date) {
	}

	/**
	 * Decides whether the history shows that an inspection on the day concerned the current holder. The earlier permits
	 * show it when the latest one issued on or before the day was the current holder's and no later one was another's. The
	 * earlier owners show it when the day was within a period of the current holder, or every other owner had left before
	 * the day, and the day was within no period of another owner. One that shows it is enough when the other tells nothing:
	 * no earlier owners, or no earlier permit issued on or before the day. When either tells of another holder, the
	 * history shows nothing.
	 *
	 * @param  day the day of the inspection
	 * @return     what shows it; empty when the history does not, or the current holder or the day is unknown
	 */
	public Optional<Evidence> sameHolderOn(final LocalDate day) {
		if (holder == null || day == null) {
			return Optional.empty();
		}
		final var byPermits = byPermits(day);
		final var byOwners = byOwners(day);
		if (byPermits.tells() == Tells.SAME && byOwners.tells() != Tells.OTHER) {
			return Optional.of(new Evidence(Source.EARLIER_PERMIT, byPermits.date()));
		}
		if (byPermits.tells() == Tells.NOTHING && byOwners.tells() == Tells.SAME) {
			return Optional.of(new Evidence(Source.OWNER_HISTORY, byOwners.date()));
		}
		return Optional.empty();
	}

	private Verdict byPermits(final LocalDate day) {
		final var dated = permits.stream().filter(permit -> permit.issued() != null).toList();
		EarlierPermit latest = null;
		for (final var permit : dated) {
			// Of two issued the same day, the one the source gave last is the latest
			if (!permit.issued().isAfter(day) && (latest == null || !permit.issued().isBefore(latest.issued()))) {
				latest = permit;
			}
		}
		if (latest == null) {
			return Verdict.NO_TELLING;
		}
		final var anotherSince = dated.stream().anyMatch(permit -> permit.issued().isAfter(day) && !holder.equals(permit.holder()));
		return holder.equals(latest.holder()) && !anotherSince ? new Verdict(Tells.SAME, latest.issued()) : Verdict.ANOTHER;
	}

	private Verdict byOwners(final LocalDate day) {
		if (owners.isEmpty()) {
			return Verdict.NO_TELLING;
		}
		final var others = owners.stream().filter(owner -> !holder.equals(owner.holder())).toList();
		if (others.stream().anyMatch(owner -> within(owner, day))) {
			return Verdict.ANOTHER;
		}
		if (owners.stream().anyMatch(owner -> holder.equals(owner.holder()) && within(owner, day))) {
			return new Verdict(Tells.SAME, null);
		}
		if (others.stream().allMatch(owner -> owner.to() != null && owner.to().isBefore(day))) {
			return new Verdict(Tells.SAME, others.stream().map(EarlierOwner::to).max(Comparator.naturalOrder()).orElse(null));
		}
		return Verdict.ANOTHER;
	}

	private static boolean within(final EarlierOwner owner, final LocalDate day) {
		return owner.from() != null && owner.to() != null && !owner.from().isAfter(day) && !owner.to().isBefore(day);
	}

	@Override
	public String toString() {
		return "HolderHistory[permits=" + permits.size() + ", owners=" + owners.size() + "]";
	}

	private enum Tells {
		SAME,
		OTHER,
		NOTHING
	}

	private record Verdict(Tells tells, LocalDate date) {

		private static final Verdict ANOTHER = new Verdict(Tells.OTHER, null);
		private static final Verdict NO_TELLING = new Verdict(Tells.NOTHING, null);
	}
}
