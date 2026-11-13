package se.sundsvall.aotloader.support;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The fields left out of a JSON parameter since their values do not have the form its schema wants, each as the path
 * of the field and why, never with the value. Not thread-safe; one belongs to the JSON parameter it is built for.
 */
public final class Omissions {

	private static final String NOT_AN_EMAIL_ADDRESS = "har inte formen av en e-postadress";

	private final List<String> omitted = new ArrayList<>();

	/**
	 * @param  path  the path of the field in the JSON parameter
	 * @param  value the value from the source
	 * @param  form  the test of the form the schema wants
	 * @param  why   what the value lacks, as the report tells it
	 * @return       the value without blanks at the start and the end when it has the form; null when it is blank, or does
	 *               not have the form, which is then noted
	 */
	public String checked(final String path, final String value, final Predicate<String> form, final String why) {
		final var stripped = Texts.trimToNull(value);
		if (stripped == null || form.test(stripped)) {
			return stripped;
		}
		omit(path, why);
		return null;
	}

	/**
	 * @return the email address as {@link #checked} gives it, by the form {@link FieldForms#isEmailAddress} tests
	 */
	public String emailAddress(final String path, final String value) {
		return checked(path, value, FieldForms::isEmailAddress, NOT_AN_EMAIL_ADDRESS);
	}

	/**
	 * Notes a field that is left out, and why.
	 */
	public void omit(final String path, final String why) {
		omitted.add(path + ": " + why);
	}

	/**
	 * @return the fields left out, in the order they were noted
	 */
	public List<String> list() {
		return List.copyOf(omitted);
	}
}
