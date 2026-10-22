package se.sundsvall.aotloader.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

import static java.util.stream.Collectors.joining;

/**
 * Text handling shared by the readers and the mappers (section 7.5: blanks at the start and end are removed and empty
 * values are left out).
 */
public final class Texts {

	/** The dash of titles and ranges. */
	public static final String DASH = "–";

	private static final String ELLIPSIS = "…";
	// Also the no-break space, which \s alone does not match
	private static final Pattern BLANKS = Pattern.compile("[\\s\\p{Z}]+");

	private Texts() {}

	/**
	 * @return the title "prefix – name", e.g. "Serveringstillstånd – Krogen"
	 */
	public static String title(final String prefix, final String name) {
		return prefix + " " + DASH + " " + name;
	}

	/**
	 * @return the range "start–end", as Draken writes a range of times, with a missing end left empty, e.g. "11:00–"
	 */
	public static String range(final String start, final String end) {
		return Optional.ofNullable(start).orElse("") + DASH + Optional.ofNullable(end).orElse("");
	}

	/**
	 * @return the range "start – end", as Draken writes a range of days, with a missing end left empty, e.g. "05-01 –"
	 */
	public static String dateRange(final String start, final String end) {
		return (Optional.ofNullable(start).orElse("") + " " + DASH + " " + Optional.ofNullable(end).orElse("")).strip();
	}

	/**
	 * @return the value without any blanks, also those inside it, such as the one in "852 30"; null when nothing remains
	 */
	public static String withoutBlanks(final String value) {
		return value == null ? null : trimToNull(BLANKS.matcher(value).replaceAll(""));
	}

	/**
	 * @return the value without blanks at the start and the end, or null when nothing remains
	 */
	public static String trimToNull(final String value) {
		if (value == null) {
			return null;
		}
		final var trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * @return true when the value is null or blank
	 */
	public static boolean isBlank(final String value) {
		return trimToNull(value) == null;
	}

	/**
	 * Shortens a value to a maximum length. A shortened value ends with the suffix, or with an ellipsis when the suffix is
	 * null.
	 *
	 * @param  value     the value, may be null
	 * @param  maxLength the maximum length, longer than the suffix
	 * @param  suffix    what a shortened value ends with
	 * @return           the value, shortened when needed
	 */
	public static String truncate(final String value, final int maxLength, final String suffix) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		final var end = suffix == null ? ELLIPSIS : suffix;
		if (end.length() >= maxLength) {
			throw new IllegalArgumentException("The suffix must be shorter than the maximum length");
		}
		return value.substring(0, cutBefore(value, maxLength - end.length())).stripTrailing() + end;
	}

	/**
	 * Splits a value into parts of at most the maximum length, at a blank when there is one in the second half of a part.
	 *
	 * @param  value     the value, may be null
	 * @param  maxLength the maximum length of a part, at least 2
	 * @return           the parts, empty for a blank value
	 */
	public static List<String> split(final String value, final int maxLength) {
		if (maxLength < 2) {
			throw new IllegalArgumentException("The maximum length must be at least 2");
		}
		final var remaining = trimToNull(value);
		final var parts = new ArrayList<String>();
		if (remaining == null) {
			return parts;
		}
		var rest = remaining;
		while (rest.length() > maxLength) {
			var cut = rest.lastIndexOf(' ', maxLength);
			if (cut < maxLength / 2) {
				cut = cutBefore(rest, maxLength);
			}
			parts.add(rest.substring(0, cut).strip());
			rest = rest.substring(cut).strip();
		}
		if (!rest.isEmpty()) {
			parts.add(rest);
		}
		return parts;
	}

	/**
	 * @return the index to cut the value at to keep at most the length, one less when the cut would part a character
	 *         written as two, such as an emoji
	 */
	private static int cutBefore(final String value, final int length) {
		return length > 0 && Character.isHighSurrogate(value.charAt(length - 1)) && Character.isLowSurrogate(value.charAt(length)) ? length - 1 : length;
	}

	/**
	 * @return the non-blank values, trimmed, joined with the delimiter, or null when there are none
	 */
	public static String join(final String delimiter, final Collection<String> values) {
		final var joined = String.join(delimiter, values.stream().map(Texts::trimToNull).filter(Objects::nonNull).toList());
		return joined.isEmpty() ? null : joined;
	}

	/**
	 * @return the non-blank values, trimmed, joined with a blank, or null when there are none
	 */
	public static String joinWords(final String... values) {
		return join(" ", Arrays.asList(values));
	}

	/**
	 * Writes a text of lines as HTML, one paragraph per line that is not blank, with the characters HTML gives a meaning
	 * escaped. Draken shows the description of an errand in an editor that reads HTML, where a line break of plain text is
	 * only a blank.
	 *
	 * @return the paragraphs, "&lt;p&gt;line&lt;/p&gt;" after each other, or null when the text has no line that is not
	 *         blank
	 */
	public static String paragraphs(final String text) {
		if (text == null) {
			return null;
		}
		final var paragraphs = text.lines()
			.map(Texts::trimToNull)
			.filter(Objects::nonNull)
			.map(line -> "<p>" + escape(line) + "</p>")
			.collect(joining());
		return paragraphs.isEmpty() ? null : paragraphs;
	}

	/**
	 * @return the heading as an HTML paragraph in bold, "&lt;p&gt;&lt;strong&gt;heading&lt;/strong&gt;&lt;/p&gt;", escaped
	 *         as {@link #paragraphs} escapes a line
	 */
	public static String boldParagraph(final String heading) {
		return "<p><strong>" + escape(heading) + "</strong></p>";
	}

	private static String escape(final String text) {
		return HtmlUtils.htmlEscape(text, StandardCharsets.UTF_8.name());
	}
}
