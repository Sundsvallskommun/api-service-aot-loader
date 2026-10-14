package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import se.sundsvall.aotloader.service.model.FileReference.FileFolder;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;

/**
 * The inspection visits of a source: the rows of Tillsyn_Tillfälle with their remarks and files, and the texts of the
 * code lists of the kinds of inspection and the remarks. AlkT and AlkOL have the same tables. The visits hold free text
 * that may name persons, so they are never logged or stored.
 *
 * @param visits      the visits
 * @param remarks     the remarks by TillsynsTillfalleNr
 * @param files       the names of the files of Tillsyn_Tillfälle_Filer by TillsynsTillfalleNr
 * @param kindTexts   the texts of the kinds of inspection by code
 * @param remarkTexts the texts of the remarks by code
 */
public record InspectionVisitRows(
	List<VisitRow> visits,
	Map<Integer, List<RemarkRow>> remarks,
	Map<Integer, List<String>> files,
	Map<String, String> kindTexts,
	Map<String, String> remarkTexts) {

	/**
	 * @return no visits at all
	 */
	public static InspectionVisitRows empty() {
		return new InspectionVisitRows(List.of(), Map.of(), Map.of(), Map.of(), Map.of());
	}

	@Override
	public String toString() {
		return "InspectionVisitRows[visits=" + visits.size() + "]";
	}

	/**
	 * @return the names of the files by visit, leaving out rows without visit or name
	 */
	public static Map<Integer, List<String>> filesByVisit(final List<VisitFileRow> rows) {
		return rows.stream()
			.filter(row -> row.tillfalleNr() != null && row.name() != null)
			.collect(groupingBy(VisitFileRow::tillfalleNr, mapping(VisitFileRow::name, toList())));
	}

	public List<RemarkRow> remarksOf(final Integer tillfalleNr) {
		return remarks.getOrDefault(tillfalleNr, emptyList());
	}

	public List<String> filesOf(final Integer tillfalleNr) {
		return files.getOrDefault(tillfalleNr, emptyList());
	}

	/**
	 * Hands every file reference of the visit to the consumer with its directory: the document the system generated, the
	 * attached file, the files of Tillsyn_Tillfälle_Filer and those of the remarks. A reference may be null.
	 */
	public void forEachFile(final VisitRow visit, final BiConsumer<String, FileFolder> consumer) {
		consumer.accept(visit.worddokument(), DOCUMENTS);
		consumer.accept(visit.bifogadfil(), ATTACHMENTS);
		filesOf(visit.tillfalleNr()).forEach(name -> consumer.accept(name, ATTACHMENTS));
		remarksOf(visit.tillfalleNr()).forEach(remark -> consumer.accept(remark.bifogadfil(), ATTACHMENTS));
	}

	/**
	 * @return the text of the kind of inspection, or the code itself when the code list does not have it
	 */
	public String kindText(final String code) {
		return CodeTexts.textOf(kindTexts, code);
	}

	/**
	 * @return the text of the remark, or the code itself when the code list does not have it
	 */
	public String remarkText(final String code) {
		return CodeTexts.textOf(remarkTexts, code);
	}

	/**
	 * A row of Tillsyn_Tillfälle.
	 *
	 * @param tillfalleNr  TillsynsTillfalleNr, the id of the visit
	 * @param objektId     the place: ObjektID of the serving place in AlkT, FSID of the sales place in AlkOL
	 * @param datum        the day of the visit, StartDatum
	 * @param startTid     the time the visit started, as AlkT writes it, or "hh:mm" from AlkOL
	 * @param slutTid      the time the visit ended, the same way
	 * @param tillsynsart  the code of the kind of inspection, Tillsynsform
	 * @param fritext      the note of the visit
	 * @param worddokument the document the system generated for the visit
	 * @param bifogadfil   the file attached to the visit
	 */
	public record VisitRow(
		Integer tillfalleNr,
		Integer objektId,
		LocalDate datum,
		String startTid,
		String slutTid,
		String tillsynsart,
		String fritext,
		String worddokument,
		String bifogadfil) {

		@Override
		public String toString() {
			return "VisitRow[tillfalleNr=" + tillfalleNr + "]";
		}
	}

	/**
	 * A row of Tillsyn_Anmärkningar.
	 *
	 * @param tillfalleNr the visit
	 * @param code        the code of the remark
	 * @param notering    the note of the remark; AlkOL has none
	 * @param bifogadfil  the file attached to the remark; AlkOL has none
	 */
	public record RemarkRow(Integer tillfalleNr, String code, String notering, String bifogadfil) {

		@Override
		public String toString() {
			return "RemarkRow[tillfalleNr=" + tillfalleNr + ", code=" + code + "]";
		}
	}

	/**
	 * A row of Tillsyn_Tillfälle_Filer.
	 *
	 * @param tillfalleNr the visit
	 * @param name        the name of the file
	 */
	public record VisitFileRow(Integer tillfalleNr, String name) {
	}
}
