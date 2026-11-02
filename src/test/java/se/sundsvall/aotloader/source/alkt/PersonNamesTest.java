package se.sundsvall.aotloader.source.alkt;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.aotloader.source.alkt.PersonNames.Split;

import static org.assertj.core.api.Assertions.assertThat;

class PersonNamesTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", value = {
		// The first name first, the most common order
		"Anna Svensson | Anna | Svensson",
		"Anna Maria Lind | Anna Maria | Lind",
		"Karin Lund-Svensson | Karin | Lund-Svensson",
		"Xyz Qwe | Xyz | Qwe",
		"Östen Ek | Östen | Ek",
		// The last name first when the last word is a first name and the first word is not
		"Svensson Anna | Anna | Svensson",
		"Qwe Anna | Anna | Qwe",
		"Svensson Anna Maria | Anna Maria | Svensson",
		"Lund-Svensson Karin | Karin | Lund-Svensson",
		// The last name first when the first word is a last name and the last word is neither
		"Andersson Xyz | Xyz | Andersson",
		"berg kalle | kalle | berg",
		// A word that is both a first name and a last name tells nothing alone
		"Jason Berg | Jason | Berg",
		"Berg Jason | Jason | Berg",
		// After a comma the first name follows the last name
		"Svensson, Anna | Anna | Svensson",
		"Svensson,Anna Maria | Anna Maria | Svensson",
		", Anna | Anna | -",
		// A prefix belongs to the last name
		"Anna von Platen | Anna | von Platen",
		"von Platen Anna | Anna | von Platen",
		"Carl af Klint | Carl | af Klint",
		// An initial is no first name
		"A. Svensson | A. | Svensson",
		// A name of one word is the first name
		"Zelda | Zelda | -"
	})
	void theNameIsSplitByTheOrderItsWordsTell(final String name, final String firstName, final String lastName) {
		assertThat(PersonNames.split(name)).isEqualTo(new Split(firstName, lastName));
	}

	@Test
	void everyBlankAroundTheCommaBecomesOne() {
		assertThat(PersonNames.split("Berg ,\tAnna\t Maria")).isEqualTo(new Split("Anna Maria", "Berg"));
	}

	@Test
	void theNamesAreNotShown() {
		assertThat(PersonNames.split("Tolvan Tolvansson")).hasToString("Split[firstName=true, lastName=true]");
	}
}
