package se.sundsvall.aotloader.support;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Optional;
import se.sundsvall.aotloader.service.model.LegalId;

import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;

/**
 * Normalizes organization numbers and personal identity numbers as section 7.2 describes.
 */
public final class LegalIds {

	private static final String ORGANIZATION_PREFIX = "16";

	private LegalIds() {}

	/**
	 * Normalizes a number from a source. Hyphens, plus signs and blanks are removed. Ten digits with a third digit of 2 or
	 * more is an organization number. Any other ten digits is a personal identity number, whose century is chosen so that
	 * the date of birth is not after today and the age is below 100, or at least 100 when the number was written with a
	 * plus sign. Twelve digits is a personal identity number with its century, or an organization number when it starts
	 * with 16.
	 *
	 * @param  raw   the number as the source has it
	 * @param  today the date the century is chosen from
	 * @return       the normalized number, or empty when the number is missing or has another format
	 */
	public static Optional<LegalId> normalize(final String raw, final LocalDate today) {
		if (raw == null) {
			return Optional.empty();
		}
		final var plus = raw.indexOf('+') >= 0;
		final var digits = raw.replaceAll("[-+\\s]", "");
		if (!digits.matches("\\d+")) {
			return Optional.empty();
		}
		return switch (digits.length()) {
			case 10 -> fromTenDigits(digits, plus, today);
			case 12 -> fromTwelveDigits(digits, today);
			default -> Optional.empty();
		};
	}

	/**
	 * @return true when the last digit is the check digit of the ten digits the number ends with, by the Luhn algorithm
	 *         that every Swedish personal identity number and organization number follows
	 */
	public static boolean hasValidCheckDigit(final LegalId legalId) {
		final var number = legalId.number();
		final var digits = number.substring(number.length() - 10);
		var sum = 0;
		for (var i = 0; i < 9; i++) {
			final var product = (digits.charAt(i) - '0') * (i % 2 == 0 ? 2 : 1);
			sum += product > 9 ? product - 9 : product;
		}
		return (10 - sum % 10) % 10 == digits.charAt(9) - '0';
	}

	/**
	 * @return the number as it is printed, with a hyphen before the last four digits, e.g. 556677-8899 or 19121212-1212;
	 *         null for null
	 */
	public static String format(final LegalId legalId) {
		if (legalId == null) {
			return null;
		}
		final var number = legalId.number();
		return number.substring(0, number.length() - 4) + "-" + number.substring(number.length() - 4);
	}

	/**
	 * @return the number without hyphens, plus signs and blanks, for comparing numbers written in different ways; empty
	 *         when nothing remains
	 */
	public static Optional<String> digitsOf(final String raw) {
		return Optional.ofNullable(raw)
			.map(value -> value.replaceAll("[-+\\s]", ""))
			.filter(value -> !value.isEmpty());
	}

	private static Optional<LegalId> fromTenDigits(final String digits, final boolean plus, final LocalDate today) {
		if (digits.charAt(2) >= '2') {
			return Optional.of(new LegalId(digits, ENTERPRISE));
		}
		final var twoDigitYear = Integer.parseInt(digits.substring(0, 2));
		var year = today.getYear() / 100 * 100 + twoDigitYear;
		final var birthDate = birthDate(year, digits.substring(2, 6));
		if (birthDate.isEmpty()) {
			return Optional.empty();
		}
		if (birthDate.get().isAfter(today)) {
			year -= 100;
		}
		if (plus) {
			year -= 100;
		}
		return birthDate(year, digits.substring(2, 6))
			.map(date -> new LegalId("%04d%s".formatted(date.getYear(), digits.substring(2)), PRIVATE));
	}

	private static Optional<LegalId> fromTwelveDigits(final String digits, final LocalDate today) {
		if (digits.startsWith(ORGANIZATION_PREFIX) && digits.charAt(4) >= '2') {
			return Optional.of(new LegalId(digits.substring(2), ENTERPRISE));
		}
		return birthDate(Integer.parseInt(digits.substring(0, 4)), digits.substring(4, 8))
			.filter(date -> !date.isAfter(today))
			.map(_ -> new LegalId(digits, PRIVATE));
	}

	/**
	 * The date of birth of a personal identity number, where a coordination number has 60 added to the day.
	 */
	private static Optional<LocalDate> birthDate(final int year, final String monthAndDay) {
		final var month = Integer.parseInt(monthAndDay.substring(0, 2));
		var day = Integer.parseInt(monthAndDay.substring(2, 4));
		if (day > 60) {
			day -= 60;
		}
		try {
			return Optional.of(LocalDate.of(year, month, day));
		} catch (final DateTimeException e) {
			return Optional.empty();
		}
	}
}
