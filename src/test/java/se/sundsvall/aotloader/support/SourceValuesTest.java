package se.sundsvall.aotloader.support;

import java.time.LocalDate;
import java.time.MonthDay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SourceValuesTest {

	@ParameterizedTest
	@CsvSource({
		"2026-05-01, 2026-05-01",
		" 2026-05-01 , 2026-05-01",
		"2026-05-01 00:00:00, 2026-05-01",
		"2026-05-01T13:45, 2026-05-01",
		"2026-05-01 13:45:10.123, 2026-05-01",
		"20260501, 2026-05-01",
		"2024-02-29, 2024-02-29"
	})
	void parseDate(final String value, final LocalDate expected) {
		assertThat(SourceValues.parseDate(value)).contains(expected);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
		" ", "2026-13-01", "2026-02-30", "2025-02-29", "1 maj", "05-01", "2026/05/01", "2026-5-1", "x2026-05-01",
		"2026-05-01 13", "2026-05-01 xx", "2026-05-01T13:45:1", "2026-05-01 13:45:10.", "2026-05-01  13:45", "2026-05-01T"
	})
	void parseDateRejects(final String value) {
		assertThat(SourceValues.parseDate(value)).isEmpty();
	}

	@ParameterizedTest
	@CsvSource({
		"1 maj, 5, 1",
		"01 maj, 5, 1",
		"30 sep, 9, 30",
		"30 sept, 9, 30",
		"30 sept., 9, 30",
		"31 okt, 10, 31",
		"1 juni, 6, 1",
		"1 JUNI, 6, 1",
		"15 augusti, 8, 15",
		"1maj, 5, 1",
		"05-01, 5, 1",
		"02-29, 2, 29",
		"1 dec, 12, 1"
	})
	void parseDayOfYear(final String value, final int month, final int day) {
		assertThat(SourceValues.parseDayOfYear(value)).contains(MonthDay.of(month, day));
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
		" ", "31 jun", "1 mjau", "maj", "13-01", "2026-05-01", "5-1", "32 jan"
	})
	void parseDayOfYearRejects(final String value) {
		assertThat(SourceValues.parseDayOfYear(value)).isEmpty();
	}

	@ParameterizedTest
	@CsvSource({
		"11:00, 11:00",
		"1:00, 01:00",
		"01.30, 01:30",
		" 23:59 , 23:59",
		"24:00, 24:00",
		"00:00, 00:00"
	})
	void parseTime(final String value, final String expected) {
		assertThat(SourceValues.parseTime(value)).contains(expected);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
		" ", "24:01", "25:00", "11:60", "11", "11:0", "kl 11"
	})
	void parseTimeRejects(final String value) {
		assertThat(SourceValues.parseTime(value)).isEmpty();
	}

	@Test
	void format() {
		assertThat(SourceValues.format(LocalDate.of(2026, 1, 2))).isEqualTo("2026-01-02");
		assertThat(SourceValues.format((LocalDate) null)).isNull();
		assertThat(SourceValues.format(MonthDay.of(5, 1))).isEqualTo("05-01");
	}
}
