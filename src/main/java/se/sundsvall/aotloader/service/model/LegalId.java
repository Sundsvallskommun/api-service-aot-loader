package se.sundsvall.aotloader.service.model;

import static java.util.Objects.requireNonNull;

/**
 * A normalized organization number or personal identity number. It is personal data: it is kept in memory only, and
 * {@link #toString()} never shows the number.
 *
 * @param number the number, 10 digits for ENTERPRISE and 12 digits for PRIVATE
 * @param type   the type of number
 */
public record LegalId(String number, PartyType type) {

	public LegalId {
		requireNonNull(number, "number");
		requireNonNull(type, "type");
	}

	@Override
	public String toString() {
		return "LegalId[" + type + "]";
	}
}
