package se.sundsvall.aotloader.source;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.support.Texts;

import static java.util.stream.Collectors.joining;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_MAX_LENGTH;

/**
 * Collects parameters and leaves out those without values (section 7.3.3: empty values are left out).
 */
public final class ParameterList {

	private static final String CELL_SEPARATOR = "|";
	private static final int MAX_LEADING_CELL_LENGTH = 255;

	private final List<Parameter> parameters = new ArrayList<>();

	public ParameterList add(final String key, final String displayName, final String value) {
		return add(key, displayName, value == null ? List.of() : List.of(value));
	}

	public ParameterList add(final String key, final String displayName, final List<String> values) {
		final var nonBlank = values.stream().filter(Objects::nonNull).map(Texts::trimToNull).filter(Objects::nonNull).toList();
		if (!nonBlank.isEmpty()) {
			parameters.add(new Parameter(key, displayName, nonBlank));
		}
		return this;
	}

	public ParameterList add(final Parameter parameter) {
		final var nonBlank = parameter.values().stream().filter(Objects::nonNull).map(Texts::trimToNull).filter(Objects::nonNull).toList();
		if (!nonBlank.isEmpty()) {
			parameters.add(new Parameter(parameter.key(), parameter.displayName(), parameter.group(), nonBlank));
		}
		return this;
	}

	/**
	 * Adds a table, which Draken shows with a row per value under the heading of the group: the columns are the display
	 * name and the cells of a row a value, each separated by "|" (decision 69). A "|" or a line break in a cell becomes a
	 * blank, since it would part the cells. A row longer than a value may be goes on in rows of its own with the leading
	 * cells blank, so that nothing of its last cell is cut. A row without anything in it is left out, and so is a table
	 * without rows.
	 *
	 * @param columns the headings of the columns
	 * @param rows    the rows, each with a cell per column, a cell null when it is empty
	 */
	public ParameterList table(final String key, final String group, final List<String> columns, final List<List<String>> rows) {
		final var values = rows.stream()
			.filter(row -> row.stream().anyMatch(cell -> !Texts.isBlank(cell)))
			.flatMap(ParameterList::tableRows)
			.toList();
		if (!values.isEmpty()) {
			parameters.add(new Parameter(key, String.join(CELL_SEPARATOR, columns), group, values));
		}
		return this;
	}

	/**
	 * @return the row as one value, or as several when its last cell does not fit in one
	 */
	private static Stream<String> tableRows(final List<String> row) {
		final var leading = row.subList(0, row.size() - 1).stream()
			.map(cell -> Texts.truncate(cell(cell), MAX_LEADING_CELL_LENGTH, null))
			.map(cell -> cell == null ? "" : cell)
			.toList();
		final var start = leading.stream().map(cell -> cell + CELL_SEPARATOR).reduce("", String::concat);
		final var last = cell(row.getLast());
		if (last == null) {
			return Stream.of(start);
		}
		final var parts = Texts.split(last, ERRAND_PARAMETER_MAX_LENGTH - start.length());
		final var blankStart = CELL_SEPARATOR.repeat(leading.size());
		return Stream.concat(Stream.of(start + parts.getFirst()), parts.stream().skip(1).map(part -> blankStart + part));
	}

	private static String cell(final String text) {
		if (text == null) {
			return null;
		}
		return Texts.trimToNull(Arrays.stream(text.split("[|\\v]")).map(String::strip).filter(part -> !part.isEmpty()).collect(joining(" ")));
	}

	public List<Parameter> build() {
		return List.copyOf(parameters);
	}
}
