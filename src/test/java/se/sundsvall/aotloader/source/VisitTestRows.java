package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import se.sundsvall.aotloader.source.InspectionVisitRows.RemarkRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.StaffMarks;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;

/**
 * Builds the inspection visits for the tests, with what a test does not need left empty.
 */
public final class VisitTestRows {

	private VisitTestRows() {}

	/**
	 * @return a visit without handlers or marks about the staff, outside a round of inspection, without marks of what it
	 *         covered and without an end day
	 */
	public static VisitRow visit(final Integer tillfalleNr, final Integer objektId, final LocalDate datum, final String startTid, final String slutTid, final String tillsynsart,
		final String fritext, final String worddokument, final String bifogadfil) {
		return visit(tillfalleNr, objektId, datum, startTid, slutTid, tillsynsart, fritext, worddokument, bifogadfil, List.of(), StaffMarks.NONE);
	}

	/**
	 * @return a visit outside a round of inspection, without marks of what it covered and without an end day
	 */
	public static VisitRow visit(final Integer tillfalleNr, final Integer objektId, final LocalDate datum, final String startTid, final String slutTid, final String tillsynsart,
		final String fritext, final String worddokument, final String bifogadfil, final List<String> handlaggare, final StaffMarks staff) {
		return new VisitRow(tillfalleNr, objektId, datum, null, startTid, slutTid, tillsynsart, fritext, worddokument, bifogadfil, handlaggare, staff, null, List.of(), false);
	}

	/**
	 * @return the visits without names for the handlers or rounds of inspection
	 */
	public static InspectionVisitRows visits(final List<VisitRow> visits, final Map<Integer, List<RemarkRow>> remarks, final Map<Integer, List<String>> files,
		final Map<String, String> kindTexts, final Map<String, String> remarkTexts) {
		return visits(visits, remarks, files, kindTexts, remarkTexts, Map.of());
	}

	/**
	 * @return the visits without rounds of inspection
	 */
	public static InspectionVisitRows visits(final List<VisitRow> visits, final Map<Integer, List<RemarkRow>> remarks, final Map<Integer, List<String>> files,
		final Map<String, String> kindTexts, final Map<String, String> remarkTexts, final Map<String, String> handlerNames) {
		return new InspectionVisitRows(visits, remarks, files, kindTexts, remarkTexts, handlerNames, Map.of());
	}
}
