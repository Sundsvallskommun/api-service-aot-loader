package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.ErrandRow;

import static org.assertj.core.api.Assertions.assertThat;

class FormerHoldersTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);
	private static final ContactRow OWNER = contact(1, "556677-8899");
	private static final ContactRow OWNER_AGAIN = contact(2, "5566778899");
	private static final ContactRow FORMER = contact(3, "556677-0001");
	private static final ContactRow WITHOUT_NUMBER = contact(4, null);
	private static final Map<Integer, ContactRow> APPLICANTS = Map.of(1, OWNER, 2, OWNER_AGAIN, 3, FORMER, 4, WITHOUT_NUMBER);

	@Test
	void whatCameBeforeTheFirstErrandOfTheOwnerWasAFormerHolders() {
		final var errands = List.of(
			errand(10, 3, LocalDate.of(2015, 3, 1), LocalDate.of(2015, 4, 1), null),
			errand(11, null, LocalDate.of(2016, 1, 1), null, null),
			errand(12, 4, null, null, LocalDate.of(2017, 6, 1)),
			errand(13, 2, LocalDate.of(2018, 1, 1), null, null),
			errand(14, null, LocalDate.of(2018, 1, 1), null, null),
			errand(15, 1, LocalDate.of(2010, 1, 1), null, null),
			errand(16, 3, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null));

		final var former = FormerHolders.of(errands, APPLICANTS, OWNER, TODAY);

		// The owner's first errand is the earliest under either of its posts, so the later errands with an unknown applicant
		// are not a former holder's, while an errand whose applicant is a former holder is, whenever it was
		assertThat(former).isEqualTo(new FormerHolders(Set.of(10, 16), LocalDate.of(2009, 12, 31)));
	}

	@Test
	void withoutAnErrandOfTheOwnerTheFormerHolderLastedUntilItsLastErrandClosed() {
		final var errands = List.of(
			errand(10, 3, LocalDate.of(2015, 3, 1), LocalDate.of(2015, 4, 1), null),
			errand(11, 3, LocalDate.of(2016, 1, 1), null, LocalDate.of(2015, 12, 1)),
			errand(12, null, LocalDate.of(2016, 1, 2), null, null),
			errand(13, null, null, null, LocalDate.of(2015, 5, 1)),
			errand(14, null, null, null, null));

		final var former = FormerHolders.of(errands, APPLICANTS, OWNER, TODAY);

		assertThat(former).isEqualTo(new FormerHolders(Set.of(10, 11, 13), LocalDate.of(2016, 1, 1)));
		assertThat(former.isUpTo(LocalDate.of(2016, 1, 1))).isTrue();
		assertThat(former.isUpTo(LocalDate.of(2016, 1, 2))).isFalse();
		assertThat(former.isUpTo(null)).isFalse();
	}

	@Test
	void aPlaceWithoutAnErrandOfAFormerHolderHasNothingOfOne() {
		final var errands = List.of(
			errand(10, 1, LocalDate.of(2018, 1, 1), null, null),
			errand(11, null, LocalDate.of(2015, 1, 1), null, null),
			errand(12, 4, LocalDate.of(2014, 1, 1), null, null));

		final var former = FormerHolders.of(errands, APPLICANTS, OWNER, TODAY);

		assertThat(former).isEqualTo(new FormerHolders(Set.of(), null));
		assertThat(former.isUpTo(LocalDate.of(2000, 1, 1))).isFalse();
		// Nor has a place without an owner, since no applicant can be told from it
		assertThat(FormerHolders.of(List.of(errand(10, 3, LocalDate.of(2015, 3, 1), null, null)), APPLICANTS, null, TODAY)).isEqualTo(new FormerHolders(Set.of(), null));
		assertThat(FormerHolders.of(List.of(), APPLICANTS, OWNER, TODAY)).isEqualTo(new FormerHolders(Set.of(), null));
	}

	@Test
	void twoPostsWithOneNumberAreOneHolder() {
		assertThat(PartyRows.sameHolder(OWNER, OWNER_AGAIN, TODAY)).isTrue();
		assertThat(PartyRows.otherHolder(OWNER, OWNER_AGAIN, TODAY)).isFalse();
		assertThat(PartyRows.otherHolder(OWNER, FORMER, TODAY)).isTrue();
		assertThat(PartyRows.sameHolder(WITHOUT_NUMBER, WITHOUT_NUMBER, TODAY)).isFalse();
		assertThat(PartyRows.otherHolder(OWNER, WITHOUT_NUMBER, TODAY)).isFalse();
		assertThat(PartyRows.otherHolder(null, OWNER, TODAY)).isFalse();
	}

	private static ErrandRow errand(final Integer arendeId, final Integer sokandeId, final LocalDate oppnad, final LocalDate avslutad, final LocalDate upplagd) {
		return new ErrandRow(arendeId, 1, "IT", null, oppnad, avslutad, sokandeId, null, List.of(), upplagd);
	}

	private static ContactRow contact(final Integer id, final String number) {
		return new ContactRow(id, number, "Bolaget AB", null, null, null, null, null, null, null);
	}
}
