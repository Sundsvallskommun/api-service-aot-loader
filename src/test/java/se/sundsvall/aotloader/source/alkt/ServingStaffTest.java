package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.StaffRow;
import se.sundsvall.aotloader.support.JsonFields;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ServingStaffTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
	private static final JsonMapper JSON = JsonMapper.builder().build();

	@Test
	void eachPersonHasTheNameSplitTheDateOfBirthAndTheDayInTheOrderOfTheNames() {
		final var staff = ServingStaff.of(List.of(
			new StaffRow(1, "Östen Ek", "19700101-1234", LocalDate.of(2020, 1, 1)),
			new StaffRow(1, "Anna Maria Berg", "850412-1234", LocalDate.of(2021, 6, 1)),
			new StaffRow(1, "Lind, Åsa", null, null),
			new StaffRow(1, "Zelda", "990303", LocalDate.of(2019, 3, 3))),
			TODAY);

		// Swedish puts å and ö after z; a comma puts the last name first, and a single word is the first name
		assertThat(json(staff)).isEqualTo("""
			[{"firstName":"Anna Maria","lastName":"Berg","dateOfBirth":"1985-04-12","fromDate":"2021-06-01"},\
			{"firstName":"Åsa","lastName":"Lind"},\
			{"firstName":"Zelda","dateOfBirth":"1999-03-03","fromDate":"2019-03-03"},\
			{"firstName":"Östen","lastName":"Ek","dateOfBirth":"1970-01-01","fromDate":"2020-01-01"}]""");
	}

	@Test
	void theDateOfBirthIsReadFromTheNumberAsAlktHasIt() {
		final var staff = ServingStaff.of(List.of(
			new StaffRow(1, "A Ett", "780601", null),
			new StaffRow(1, "B Två", "780601-", null),
			new StaffRow(1, "C Tre", "19780601", null),
			new StaffRow(1, "D Fyra", "7806611234", null),
			new StaffRow(1, "E Fem", "060101-1234", null),
			new StaffRow(1, "F Sex", "250101+1234", null),
			new StaffRow(1, "G Sju", "99999-9999", null),
			new StaffRow(1, "H Åtta", "781301-1234", null)),
			TODAY);

		// A coordination number has 60 added to the day, a plus sign means at least 100 years, and a number that is no date
		// gives none
		assertThat(staff).extracting(person -> person.toNode().has("dateOfBirth") ? person.toNode().get("dateOfBirth").asString() : null)
			.containsExactly("1978-06-01", "1978-06-01", "1978-06-01", "1978-06-01", "2006-01-01", "1925-01-01", null, null);
	}

	@Test
	void aPersonIsToldByTheNumberOrElseByTheName() {
		final var staff = ServingStaff.of(List.of(
			new StaffRow(1, "Anna  Berg", "800101-1234", LocalDate.of(2021, 6, 1)),
			new StaffRow(1, "Anna Bergström", "198001011234", LocalDate.of(2015, 2, 1)),
			new StaffRow(1, " anna berg ", null, LocalDate.of(2010, 1, 1)),
			new StaffRow(1, "Anna Berg", "900101-1234", null),
			new StaffRow(1, "Per Ek", null, null),
			new StaffRow(1, "per ek", "780601", LocalDate.of(2001, 1, 1))),
			TODAY);

		// The same number is one person whatever the name, with the name of its first row, its blanks made one; a row
		// without a whole number joins the first person of the same name
		assertThat(json(staff)).isEqualTo("""
			[{"firstName":"Anna","lastName":"Berg","dateOfBirth":"1980-01-01","fromDate":"2010-01-01"},\
			{"firstName":"Anna","lastName":"Berg","dateOfBirth":"1990-01-01"},\
			{"firstName":"Per","lastName":"Ek","dateOfBirth":"1978-06-01","fromDate":"2001-01-01"}]""");
	}

	@Test
	void aRowWithoutANameIsLeftOut() {
		assertThat(ServingStaff.of(List.of(new StaffRow(1, null, "800101-1234", LocalDate.of(2020, 1, 1)), new StaffRow(1, " ", null, null)), TODAY)).isEmpty();
		assertThat(ServingStaff.of(List.of(), TODAY)).isEmpty();
	}

	private static String json(final List<JsonFields> staff) {
		return JSON.writeValueAsString(staff.stream().map(JsonFields::toNode).toList());
	}
}
