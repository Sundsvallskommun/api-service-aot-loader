package se.sundsvall.aotloader.source.files;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import se.sundsvall.aotloader.support.Texts;

/**
 * Reads the legal basis a decision document of the source states, such as "8 kap. 2 § alkohollagen (2010:1622)"
 * (decision 58). The source keeps the legal basis nowhere else: its templates write it into each decision and permit
 * certificate, and a permit decided under an earlier law states that law. Only RTF documents are read, the format the
 * sources generate.
 */
public final class LegalBasis {

	private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
	private static final String RTF_START = "{\\rtf";
	// A chapter and section followed by the name of the law and its number in the statute book
	private static final Pattern REFERENCE = Pattern.compile("\\d+\\s*kap\\.?\\s*\\d+\\s*[a-z]?\\s*§[^.;§\\n]{0,80}?\\(\\d{4}:\\d+\\)", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	// The groups whose content is no text of the document, such as pictures and embedded objects
	private static final Set<String> HIDDEN_GROUPS = Set.of("pict", "shppict", "nonshppict", "object", "objdata", "datastore", "themedata", "colorschememapping", "fonttbl",
		"colortbl", "stylesheet", "info", "listtable", "listoverridetable", "rsidtbl", "latentstyles", "generator", "xmlnstbl");

	private LegalBasis() {}

	/**
	 * @param  content the document
	 * @return         the references to the law the document states, in the order it states them, joined with "; " when
	 *                 there are several; empty when the document is no RTF or states none
	 */
	public static Optional<String> of(final byte[] content) {
		if (content == null || !new String(content, 0, Math.min(content.length, RTF_START.length()), StandardCharsets.US_ASCII).equals(RTF_START)) {
			return Optional.empty();
		}
		final var references = new LinkedHashSet<String>();
		REFERENCE.matcher(text(new String(content, WINDOWS_1252))).results()
			.map(match -> match.group().replaceAll("\\s*§\\s*", " § ").replaceAll("\\s+", " ").strip())
			.forEach(references::add);
		return Optional.ofNullable(Texts.join("; ", references));
	}

	/**
	 * @return the text of an RTF document: the content of the groups that hold no text left out, the escaped characters
	 *         decoded and the control words removed
	 */
	static String text(final String rtf) {
		final var text = new StringBuilder(rtf.length() / 4);
		var index = 0;
		while (index < rtf.length()) {
			final var current = rtf.charAt(index);
			if (current == '{') {
				final var end = hiddenGroupEnd(rtf, index);
				index = end > 0 ? end : index + 1;
			} else if (current == '}') {
				index++;
			} else if (current == '\\') {
				index = controlWord(rtf, index, text);
			} else {
				if (current != '\r' && current != '\n') {
					text.append(current);
				}
				index++;
			}
		}
		return text.toString();
	}

	/**
	 * @return the index after the group that starts at the index when it holds no text, -1 when it holds text
	 */
	private static int hiddenGroupEnd(final String rtf, final int start) {
		var word = start + 1;
		if (rtf.startsWith("\\*", word)) {
			return groupEnd(rtf, start);
		}
		if (word < rtf.length() && rtf.charAt(word) == '\\') {
			word++;
			var end = word;
			while (end < rtf.length() && Character.isLetter(rtf.charAt(end))) {
				end++;
			}
			if (HIDDEN_GROUPS.contains(rtf.substring(word, end))) {
				return groupEnd(rtf, start);
			}
		}
		return -1;
	}

	/**
	 * @return the index after the brace that closes the group starting at the index, the end of the document when none does
	 */
	private static int groupEnd(final String rtf, final int start) {
		var depth = 0;
		for (var index = start; index < rtf.length(); index++) {
			final var current = rtf.charAt(index);
			if (current == '\\') {
				// An escaped brace or backslash is no brace of a group
				index++;
			} else if (current == '{') {
				depth++;
			} else if (current == '}' && --depth == 0) {
				return index + 1;
			}
		}
		return rtf.length();
	}

	/**
	 * Appends what the control word or symbol at the index stands for: a character for an escaped one, nothing for the
	 * others.
	 *
	 * @return the index after it
	 */
	private static int controlWord(final String rtf, final int start, final StringBuilder text) {
		final var next = start + 1;
		if (next >= rtf.length()) {
			return next;
		}
		final var symbol = rtf.charAt(next);
		if (symbol == '\'' && next + 2 < rtf.length()) {
			appendHex(rtf.substring(next + 1, next + 3), text);
			return next + 3;
		}
		if (!Character.isLetter(symbol)) {
			if (symbol == '\\' || symbol == '{' || symbol == '}') {
				text.append(symbol);
			} else if (symbol == '~') {
				text.append(' ');
			}
			return next + 1;
		}
		var end = next;
		while (end < rtf.length() && Character.isLetter(rtf.charAt(end))) {
			end++;
		}
		final var word = rtf.substring(next, end);
		var parameterEnd = end;
		if (parameterEnd < rtf.length() && rtf.charAt(parameterEnd) == '-') {
			parameterEnd++;
		}
		while (parameterEnd < rtf.length() && Character.isDigit(rtf.charAt(parameterEnd))) {
			parameterEnd++;
		}
		final var parameter = rtf.substring(end, parameterEnd);
		// A blank after a control word belongs to it
		final var after = parameterEnd < rtf.length() && rtf.charAt(parameterEnd) == ' ' ? parameterEnd + 1 : parameterEnd;
		switch (word) {
			case "u" -> {
				appendUnicode(parameter, text);
				return afterFallback(rtf, after);
			}
			case "par", "line", "tab", "cell", "row" -> text.append(' ');
			default -> {
				// A control word ends at its parameter
			}
		}
		return after;
	}

	/**
	 * @return the index after the character that follows a Unicode character for readers that do not know it, an escaped
	 *         one or a plain one
	 */
	private static int afterFallback(final String rtf, final int start) {
		if (rtf.startsWith("\\'", start)) {
			return Math.min(rtf.length(), start + 4);
		}
		return start < rtf.length() && rtf.charAt(start) != '\\' && rtf.charAt(start) != '{' && rtf.charAt(start) != '}' ? start + 1 : start;
	}

	private static void appendHex(final String hex, final StringBuilder text) {
		try {
			text.append(new String(new byte[] {
				(byte) Integer.parseInt(hex, 16)
			}, WINDOWS_1252));
		} catch (final NumberFormatException _) {
			// Not an escaped character after all
		}
	}

	private static void appendUnicode(final String parameter, final StringBuilder text) {
		try {
			final var code = Integer.parseInt(parameter);
			text.append((char) (code < 0 ? code + 65536 : code));
		} catch (final NumberFormatException _) {
			// A \\u without a number is no character
		}
	}
}
