package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.StaffRow;

import static org.assertj.core.api.Assertions.assertThat;

class ServingStaffTest {

	@Test
	void eachPersonIsARowWithTheNameAndTheDayInTheOrderOfTheNames() {
		final var rows = ServingStaff.rows(List.of(
			new StaffRow(1, "Östen Ek", LocalDate.of(2020, 1, 1)),
			new StaffRow(1, "Anna Berg", LocalDate.of(2021, 6, 1)),
			new StaffRow(1, "Åsa Lind", null),
			new StaffRow(1, "Zelda Ström", LocalDate.of(2019, 3, 3))));

		// Swedish puts å and ö after z
		assertThat(rows).containsExactly(
			List.of("Anna Berg", "2021-06-01"),
			List.of("Zelda Ström", "2019-03-03"),
			Arrays.asList("Åsa Lind", null),
			List.of("Östen Ek", "2020-01-01"));
	}

	@Test
	void aNameThatOccursMoreThanOnceIsOnePersonWithTheEarliestDay() {
		final var rows = ServingStaff.rows(List.of(
			new StaffRow(1, "Anna  Berg", LocalDate.of(2021, 6, 1)),
			new StaffRow(1, " anna berg ", LocalDate.of(2015, 2, 1)),
			new StaffRow(1, "ANNA BERG", null),
			new StaffRow(1, "Per Ek", null),
			new StaffRow(1, "per ek", null)));

		// The name as the first row writes it, its blanks made one
		assertThat(rows).containsExactly(List.of("Anna Berg", "2015-02-01"), Arrays.asList("Per Ek", null));
	}

	@Test
	void aRowWithoutANameIsLeftOut() {
		assertThat(ServingStaff.rows(List.of(new StaffRow(1, null, LocalDate.of(2020, 1, 1)), new StaffRow(1, " ", null)))).isEmpty();
		assertThat(ServingStaff.rows(List.of())).isEmpty();
	}
}
