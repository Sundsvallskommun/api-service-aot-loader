package se.sundsvall.aotloader.support;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.PartyType;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;

class LegalIdsTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

	@ParameterizedTest
	@CsvSource({
		"556677-8899, 5566778899",
		"5566778899, 5566778899",
		"556677 8899, 5566778899",
		"16556677-8899, 5566778899",
		"212000-1355, 2120001355"
	})
	void organizationNumber(final String raw, final String expected) {
		assertThat(LegalIds.normalize(raw, TODAY)).contains(new LegalId(expected, ENTERPRISE));
	}

	@Test
	void format() {
		assertThat(LegalIds.format(new LegalId("5566778899", ENTERPRISE))).isEqualTo("556677-8899");
		assertThat(LegalIds.format(new LegalId("191212121212", PRIVATE))).isEqualTo("19121212-1212");
		assertThat(LegalIds.format(null)).isNull();
	}

	@ParameterizedTest
	@CsvSource({
		// The date of birth is not after today and the age is below 100
		"800101-1234, 198001011234",
		"261012-1234, 202610121234",
		"261013-1234, 192610131234",
		"000229-1234, 200002291234",
		// A plus sign means 100 years or older
		"200101+1234, 192001011234",
		"261013+1234, 182610131234",
		// A coordination number has 60 added to the day
		"800161-1234, 198001611234",
		// Twelve digits keep their century
		"19800101-1234, 198001011234",
		"198001011234, 198001011234"
	})
	void personalIdentityNumber(final String raw, final String expected) {
		assertThat(LegalIds.normalize(raw, TODAY)).contains(new LegalId(expected, PRIVATE));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
		"   ", "556677-889", "55667788990", "5566A78899", "801301-1234", "800132-1234", "010229-1234", "20800101-1234", "1234567890123"
	})
	void invalid(final String raw) {
		assertThat(LegalIds.normalize(raw, TODAY)).isEmpty();
	}

	@Test
	void digitsOf() {
		assertThat(LegalIds.digitsOf(" 556677-8899 ")).contains("5566778899");
		assertThat(LegalIds.digitsOf("19800101+1234")).contains("198001011234");
		assertThat(LegalIds.digitsOf(" - ")).isEmpty();
		assertThat(LegalIds.digitsOf("556677–8899")).contains("5566778899");
		assertThat(LegalIds.digitsOf("556677 8899")).contains("5566778899");
		assertThat(LegalIds.normalize("556677‑8899", TODAY)).contains(new LegalId("5566778899", ENTERPRISE));
		assertThat(LegalIds.normalize("19121212−1212", TODAY)).contains(new LegalId("191212121212", PRIVATE));
		assertThat(LegalIds.normalize("121212— 1212", TODAY)).contains(new LegalId("201212121212", PRIVATE));
		assertThat(LegalIds.digitsOf(null)).isEmpty();
	}

	@ParameterizedTest
	@CsvSource({
		"198112189876, PRIVATE, true",
		"198112189875, PRIVATE, false",
		"198001011234, PRIVATE, false",
		"5566778899, ENTERPRISE, true",
		"5566778898, ENTERPRISE, false"
	})
	void theCheckDigitOfTheLastTenDigits(final String number, final PartyType type, final boolean valid) {
		assertThat(LegalIds.hasValidCheckDigit(new LegalId(number, type))).isEqualTo(valid);
	}

	@Test
	void toStringHidesTheNumber() {
		assertThat(new LegalId("198001011234", PRIVATE)).hasToString("LegalId[PRIVATE]");
	}
}
