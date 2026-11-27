package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.model.HolderHistory.EarlierOwner;
import se.sundsvall.aotloader.service.model.HolderHistory.EarlierPermit;
import se.sundsvall.aotloader.service.model.HolderHistory.Evidence;
import se.sundsvall.aotloader.service.model.HolderHistory.Source;

import static org.assertj.core.api.Assertions.assertThat;

class HolderHistoryTest {

	private static final String HOLDER = "5566778899";
	private static final String OTHER = "5599887766";
	private static final LocalDate DAY = LocalDate.of(2016, 6, 1);

	@Test
	void anEarlierPermitOfTheHolderShowsTheHolder() {
		final var history = new HolderHistory(HOLDER, List.of(new EarlierPermit(LocalDate.of(2010, 1, 1), OTHER), new EarlierPermit(LocalDate.of(2015, 3, 1), HOLDER)), List.of());

		assertThat(history.sameHolderOn(DAY)).contains(new Evidence(Source.EARLIER_PERMIT, LocalDate.of(2015, 3, 1)));
		// Also on the day it was issued
		assertThat(history.sameHolderOn(LocalDate.of(2015, 3, 1))).contains(new Evidence(Source.EARLIER_PERMIT, LocalDate.of(2015, 3, 1)));
		// Before it, the permit of another holder was the latest
		assertThat(history.sameHolderOn(LocalDate.of(2014, 1, 1))).isEmpty();
		// Before every earlier permit they tell nothing, and nor do owners the place has none of
		assertThat(history.sameHolderOn(LocalDate.of(2009, 1, 1))).isEmpty();
	}

	@Test
	void anotherHolderAfterTheInspectionOrAnUnreadableNumberShowsNothing() {
		// The holder had the place before another, and has it again
		final var again = new HolderHistory(HOLDER, List.of(new EarlierPermit(LocalDate.of(2010, 1, 1), HOLDER), new EarlierPermit(LocalDate.of(2017, 1, 1), OTHER)), List.of());
		final var unreadable = new HolderHistory(HOLDER, List.of(new EarlierPermit(LocalDate.of(2010, 1, 1), null)), List.of());
		final var unreadableLater = new HolderHistory(HOLDER, List.of(new EarlierPermit(LocalDate.of(2010, 1, 1), HOLDER), new EarlierPermit(LocalDate.of(2017, 1, 1), null)), List.of());

		assertThat(again.sameHolderOn(DAY)).isEmpty();
		assertThat(unreadable.sameHolderOn(DAY)).isEmpty();
		assertThat(unreadableLater.sameHolderOn(DAY)).isEmpty();
	}

	@Test
	void aPermitWithoutADayIsLeftOutAndOfTwoTheSameDayTheLastGivenIsTheLatest() {
		final var undated = new HolderHistory(HOLDER, List.of(new EarlierPermit(LocalDate.of(2010, 1, 1), HOLDER), new EarlierPermit(null, OTHER)), List.of());
		final var holderLast = new HolderHistory(HOLDER, List.of(new EarlierPermit(LocalDate.of(2010, 1, 1), OTHER), new EarlierPermit(LocalDate.of(2010, 1, 1), HOLDER)), List.of());
		final var otherLast = new HolderHistory(HOLDER, List.of(new EarlierPermit(LocalDate.of(2010, 1, 1), HOLDER), new EarlierPermit(LocalDate.of(2010, 1, 1), OTHER)), List.of());

		assertThat(undated.sameHolderOn(DAY)).contains(new Evidence(Source.EARLIER_PERMIT, LocalDate.of(2010, 1, 1)));
		assertThat(holderLast.sameHolderOn(DAY)).contains(new Evidence(Source.EARLIER_PERMIT, LocalDate.of(2010, 1, 1)));
		assertThat(otherLast.sameHolderOn(DAY)).isEmpty();
	}

	@Test
	void theEarlierOwnersShowTheHolderWhenEveryOtherOwnerHadLeft() {
		final var history = new HolderHistory(HOLDER, List.of(), List.of(new EarlierOwner(null, LocalDate.of(2012, 4, 30), OTHER), new EarlierOwner(null, LocalDate.of(2009, 1, 1), "5511223344")));

		// The last day of the latest other owner tells when the place changed hands
		assertThat(history.sameHolderOn(DAY)).contains(new Evidence(Source.OWNER_HISTORY, LocalDate.of(2012, 4, 30)));
		assertThat(history.sameHolderOn(LocalDate.of(2012, 5, 1))).contains(new Evidence(Source.OWNER_HISTORY, LocalDate.of(2012, 4, 30)));
		// On the last day of another owner, the place was still that owner's
		assertThat(history.sameHolderOn(LocalDate.of(2012, 4, 30))).isEmpty();
	}

	@Test
	void anOtherOwnerWithoutALastDayOrAnUnreadableNumberShowsNothing() {
		final var withoutEnd = new HolderHistory(HOLDER, List.of(), List.of(new EarlierOwner(LocalDate.of(2001, 1, 1), null, OTHER)));
		final var unreadable = new HolderHistory(HOLDER, List.of(), List.of(new EarlierOwner(null, LocalDate.of(2020, 1, 1), null)));

		assertThat(withoutEnd.sameHolderOn(DAY)).isEmpty();
		assertThat(unreadable.sameHolderOn(DAY)).isEmpty();
	}

	@Test
	void aPeriodOfTheHolderShowsItAndAPeriodOfAnotherOwnerShowsNothing() {
		final var holderPeriod = new HolderHistory(HOLDER, List.of(), List.of(new EarlierOwner(LocalDate.of(2015, 1, 1), LocalDate.of(2018, 12, 31), HOLDER),
			new EarlierOwner(LocalDate.of(2019, 1, 1), LocalDate.of(2020, 12, 31), OTHER)));
		final var onlyTheHolder = new HolderHistory(HOLDER, List.of(), List.of(new EarlierOwner(null, LocalDate.of(2014, 12, 31), HOLDER)));
		final var otherPeriod = new HolderHistory(HOLDER, List.of(new EarlierPermit(LocalDate.of(2010, 1, 1), HOLDER)),
			List.of(new EarlierOwner(LocalDate.of(2015, 1, 1), LocalDate.of(2017, 12, 31), OTHER)));

		assertThat(holderPeriod.sameHolderOn(DAY)).contains(new Evidence(Source.OWNER_HISTORY, null));
		// No other owner, so none that had not left
		assertThat(onlyTheHolder.sameHolderOn(DAY)).contains(new Evidence(Source.OWNER_HISTORY, null));
		// The earlier permit and the owners disagree, so neither shows the holder
		assertThat(otherPeriod.sameHolderOn(DAY)).isEmpty();
	}

	@Test
	void theEarlierPermitsDecideWhenTheOwnersAgreeOrTellNothing() {
		final var permits = List.of(new EarlierPermit(LocalDate.of(2015, 3, 1), HOLDER));
		final var agreeing = new HolderHistory(HOLDER, permits, List.of(new EarlierOwner(null, LocalDate.of(2015, 2, 28), OTHER)));
		final var disagreeing = new HolderHistory(HOLDER, permits, List.of(new EarlierOwner(null, LocalDate.of(2017, 1, 1), OTHER)));
		final var ownersAloneDisagreeing = new HolderHistory(HOLDER, List.of(new EarlierPermit(LocalDate.of(2015, 3, 1), OTHER)),
			List.of(new EarlierOwner(null, LocalDate.of(2015, 2, 28), OTHER)));

		assertThat(agreeing.sameHolderOn(DAY)).contains(new Evidence(Source.EARLIER_PERMIT, LocalDate.of(2015, 3, 1)));
		assertThat(disagreeing.sameHolderOn(DAY)).isEmpty();
		// The owners show the holder, but the latest earlier permit was another's
		assertThat(ownersAloneDisagreeing.sameHolderOn(DAY)).isEmpty();
	}

	@Test
	void withoutTheHolderOrTheDayTheHistoryShowsNothing() {
		final var permits = List.of(new EarlierPermit(LocalDate.of(2015, 3, 1), HOLDER));

		assertThat(new HolderHistory(null, permits, List.of()).sameHolderOn(DAY)).isEmpty();
		assertThat(new HolderHistory(HOLDER, permits, List.of()).sameHolderOn(null)).isEmpty();
		assertThat(new HolderHistory(HOLDER, List.of(), List.of()).sameHolderOn(DAY)).isEmpty();
	}

	@Test
	void theHistoryHidesTheNumbers() {
		final var history = new HolderHistory(HOLDER, List.of(new EarlierPermit(LocalDate.of(2015, 3, 1), OTHER)), List.of(new EarlierOwner(null, LocalDate.of(2015, 2, 28), OTHER)));

		assertThat(history.toString()).doesNotContain(HOLDER, OTHER).isEqualTo("HolderHistory[permits=1, owners=1]");
		assertThat(history.permits().getFirst().toString()).doesNotContain(OTHER).contains("2015-03-01");
		assertThat(history.owners().getFirst().toString()).doesNotContain(OTHER).contains("2015-02-28");
	}
}
