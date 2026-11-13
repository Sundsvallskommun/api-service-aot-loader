package se.sundsvall.aotloader.support;

import java.util.regex.Pattern;

/**
 * Tests of the forms the AoT schemas want of some values, as the json-schema service checks them.
 */
public final class FieldForms {

	private static final Pattern POSTAL_CODE = Pattern.compile("\\d{5}");
	// Only addresses that the format email of the json-schema service also takes, and only of plain characters, so one
	// with å, ä or ö fails
	private static final Pattern EMAIL_ADDRESS = Pattern.compile("[\\w%+-]++(?:\\.[\\w%+-]++)*+@(?:\\p{Alnum}++(?:-++\\p{Alnum}++)*+\\.)++\\p{Alpha}{2,63}+");

	private FieldForms() {}

	/**
	 * @return true when the value, written without blanks, is a Swedish postal code of five digits
	 */
	public static boolean isPostalCode(final String value) {
		return value != null && POSTAL_CODE.matcher(value).matches();
	}

	/**
	 * @return true when the value is an email address the json-schema service takes
	 */
	public static boolean isEmailAddress(final String value) {
		return value != null && EMAIL_ADDRESS.matcher(value).matches();
	}
}
