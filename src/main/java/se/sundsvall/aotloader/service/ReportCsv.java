package se.sundsvall.aotloader.service;

import java.util.List;
import java.util.Optional;
import se.sundsvall.aotloader.api.model.ReportLine;

/**
 * Writes the report of a run as CSV for Excel: semicolon separated, UTF-8 with a byte order mark.
 */
public final class ReportCsv {

	static final String BYTE_ORDER_MARK = "﻿";
	static final String HEADER = "Källa;Sort;Käll-id;Serveringsställenummer;Ställe;Utfall;Regler;Detalj;Ärendenummer";
	private static final String NEW_LINE = "\r\n";

	private ReportCsv() {}

	/**
	 * @return the rows as CSV, with a header
	 */
	public static String write(final List<ReportLine> lines) {
		final var csv = new StringBuilder(BYTE_ORDER_MARK).append(HEADER).append(NEW_LINE);
		lines.forEach(line -> csv.append(String.join(";",
			cell(line.source() == null ? null : line.source().name()),
			cell(line.kind() == null ? null : line.kind().name()),
			cell(line.sourceId()),
			cell(line.placeNumber()),
			cell(line.placeName()),
			cell(line.outcome() == null ? null : line.outcome().name()),
			cell(line.rules()),
			cell(line.detail()),
			cell(line.errandNumber()))).append(NEW_LINE));
		return csv.toString();
	}

	/**
	 * A cell, quoted, with line breaks replaced. A value that Excel would read as a formula gets a leading apostrophe.
	 */
	static String cell(final String value) {
		var text = Optional.ofNullable(value).orElse("").replaceAll("[\\r\\n]+", " | ");
		if (!text.isEmpty() && "=+-@\t".indexOf(text.charAt(0)) >= 0) {
			text = "'" + text;
		}
		return "\"" + text.replace("\"", "\"\"") + "\"";
	}
}
