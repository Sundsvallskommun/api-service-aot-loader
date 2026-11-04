package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.alkt.AlktTestRows;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_ON_SEVERAL;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_ON_SEVERAL_OWNERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNDERLYING_ERRAND_OPEN;

class SourceRulesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

	@Test
	void ownerPostsWithTheSameNumberHoweverItIsWrittenHitG7() {
		final var first = owner(1, "556677-8899");
		final var second = owner(2, "5566778899");
		final var other = owner(3, "5599887766");
		final var withoutNumber = owner(4, null);
		final var numbers = new OwnerNumbers(TODAY);
		List.of(first, second, other, withoutNumber).forEach(numbers::add);
		// Counted once however many permits the owner has
		numbers.add(other);
		numbers.add(null);
		final var hits = new ArrayList<RuleHit>();

		numbers.hits(first, hits);
		numbers.hits(other, hits);
		numbers.hits(withoutNumber, hits);
		numbers.hits(null, hits);

		assertThat(hits).containsExactly(RuleHit.of(LEGAL_ID_ON_SEVERAL_OWNERS, "AgarID 1"));
	}

	@Test
	void theRowsOfOnePlaceWithTheSameDiaryNumberCountOnceAndRowsWithoutAPlaceEach() {
		final var counts = new DiaryNumberCounts();
		counts.add("IAN-2019-1", 1);
		counts.add("IAN 2019-1", 1);
		counts.add("IAN-2019-2", 1);
		counts.add("IAN-2019-2", 2);
		counts.add("IAN-2020-1", null);
		counts.add("IAN-2020-1", null);
		counts.add(null, 3);
		final var hits = new ArrayList<RuleHit>();

		counts.hits("IAN-2019-1", null, hits);
		counts.hits("ian-2019-2", null, hits);
		counts.hits("IAN-2020-1", null, hits);
		counts.hits(" ", "det beviljande ärendet saknar diarienummer", hits);
		counts.hits(null, null, hits);

		assertThat(hits).containsExactly(
			RuleHit.of(DIARY_NUMBER_ON_SEVERAL, "ian-2019-2"),
			RuleHit.of(DIARY_NUMBER_ON_SEVERAL, "IAN-2020-1"),
			RuleHit.of(DIARY_NUMBER_MISSING, "det beviljande ärendet saknar diarienummer"),
			RuleHit.of(DIARY_NUMBER_MISSING));
	}

	@Test
	void everyErrandThatIsNotClosedHitsÄ1() {
		final var hits = new ArrayList<RuleHit>();

		SourceRules.openErrandHits(List.of(
			AlktTestRows.errand(10, 1, "01", "IAN-2019-1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null),
			AlktTestRows.errand(11, 1, "01", "IAN-2019-2", LocalDate.of(2019, 1, 1), null, null),
			AlktTestRows.errand(12, 1, "01", null, null, null, null)), hits);

		assertThat(hits).containsExactly(
			RuleHit.of(UNDERLYING_ERRAND_OPEN, "ArendeID 11, diarienummer IAN-2019-2"),
			RuleHit.of(UNDERLYING_ERRAND_OPEN, "ArendeID 12"));
	}

	private static ContactRow owner(final int id, final String number) {
		return new ContactRow(id, number, "Bolaget AB", null, null, null, null, null, null, null);
	}
}
