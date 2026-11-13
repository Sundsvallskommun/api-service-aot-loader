package se.sundsvall.aotloader.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static se.sundsvall.aotloader.service.model.ReviewRule.FILE_NOT_UPLOADED;

/**
 * The files that were not uploaded, by problem. A name may hold a person's name; the report masks numbers only.
 */
final class FileProblems {

	static final int MAX_FILE_NAMES_IN_REPORT = 20;

	private final Map<FileProblem, List<String>> names = new EnumMap<>(FileProblem.class);

	void add(final FileProblem problem, final String name) {
		names.computeIfAbsent(problem, _ -> new ArrayList<>()).add(name);
	}

	boolean has(final FileProblem problem) {
		return names.containsKey(problem);
	}

	void report(final ReportNotes notes, final String maxFileSize) {
		if (names.keySet().stream().anyMatch(FileProblem::isRuleHit)) {
			notes.rule(FILE_NOT_UPLOADED.getCode());
		}
		names.forEach((problem, files) -> notes.line(
			(problem.isRuleHit() ? FILE_NOT_UPLOADED.getCode() + " " : "") + files.size() + " " + problem.text(files.size(), maxFileSize) + " (" + list(files) + ")"));
	}

	private static String list(final List<String> files) {
		final var shown = files.stream().limit(MAX_FILE_NAMES_IN_REPORT).toList();
		final var rest = files.size() - shown.size();
		return String.join(", ", shown) + (rest > 0 ? " och %d till".formatted(rest) : "");
	}
}
