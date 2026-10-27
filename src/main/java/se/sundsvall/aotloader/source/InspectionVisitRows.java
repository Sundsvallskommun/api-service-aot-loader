package se.sundsvall.aotloader.source;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import se.sundsvall.aotloader.service.model.FileReference.FileFolder;
import se.sundsvall.aotloader.source.files.SharedFileNames;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.source.Rows.date;
import static se.sundsvall.aotloader.source.Rows.integer;
import static se.sundsvall.aotloader.source.Rows.text;

/**
 * The inspection visits of a source: the rows of Tillsyn_Tillfälle with their remarks and files, and the texts of the
 * code lists of the kinds of inspection and the remarks. AlkT and AlkOL have the same tables. The visits hold free text
 * that may name persons, so they are never logged or stored.
 *
 * @param visits       the visits
 * @param remarks      the remarks by TillsynsTillfalleNr
 * @param files        the names of the files of Tillsyn_Tillfälle_Filer by TillsynsTillfalleNr
 * @param kindTexts    the texts of the kinds of inspection by code
 * @param remarkTexts  the texts of the remarks by code
 * @param handlerNames the names of the handlers by the code the visits and the errands name them with
 * @param rounds       the rounds of inspection by their id, Tillsyn_Omgång
 */
public record InspectionVisitRows(
	List<VisitRow> visits,
	Map<Integer, List<RemarkRow>> remarks,
	Map<Integer, List<String>> files,
	Map<String, String> kindTexts,
	Map<String, String> remarkTexts,
	Map<String, String> handlerNames,
	Map<Integer, RoundRow> rounds) {

	/**
	 * @return no visits at all
	 */
	public static InspectionVisitRows empty() {
		return new InspectionVisitRows(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
	}

	@Override
	public String toString() {
		return "InspectionVisitRows[visits=" + visits.size() + "]";
	}

	/**
	 * @return the round of inspection of a row of Tillsyn_Omgång, which AlkT and AlkOL keep with the same columns
	 */
	public static RoundRow toRound(final ResultSet rs) throws SQLException {
		return new RoundRow(integer(rs, "TillsynsOmgangsID"), text(rs, "Omgangsnamn"), date(rs, "StartDatum"), date(rs, "SlutDatum"), text(rs, "Fritext"));
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
	 * Adds every file reference of the visits to the file names of the source, each with the place of its visit, or the
	 * visit itself when it has no place.
	 *
	 * @param placeKey the key of a place of the source, by its id
	 */
	public void addFileNames(final SharedFileNames.Builder names, final Function<Integer, String> placeKey) {
		visits.forEach(visit -> {
			final var place = visit.objektId() == null ? "besok:" + visit.tillfalleNr() : placeKey.apply(visit.objektId());
			forEachFile(visit, (name, folder) -> names.add(place, name, folder));
		});
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
	 * @return the names of the handlers of the visit in order, each the code itself when no name is found, without
	 *         repeating one (decision 48)
	 */
	public List<String> handlerNamesOf(final VisitRow visit) {
		return handlerNames(visit.handlaggare());
	}

	/**
	 * @return the names of the handlers in order, each the code itself when no name is found, without repeating one; the
	 *         errands name their handlers with the same codes as the visits
	 */
	public List<String> handlerNames(final Collection<String> codes) {
		return codes.stream().map(this::handlerName).distinct().toList();
	}

	/**
	 * @return the name of the handler, or the code itself when no name is found; null for a null code
	 */
	public String handlerName(final String code) {
		return CodeTexts.textOf(handlerNames, code);
	}

	/**
	 * @return the round of inspection the visit was made in, null when it was in none or the round is missing
	 */
	public RoundRow roundOf(final VisitRow visit) {
		return visit.omgangsNr() == null ? null : rounds.get(visit.omgangsNr());
	}

	/**
	 * A row of Tillsyn_Tillfälle.
	 *
	 * @param tillfalleNr  TillsynsTillfalleNr, the id of the visit
	 * @param objektId     the place: ObjektID of the serving place in AlkT, FSID of the sales place in AlkOL
	 * @param datum        the day of the visit, StartDatum
	 * @param slutDatum    the day the visit ended, SlutDatum, null when not set
	 * @param startTid     the time the visit started, as AlkT writes it, or "hh:mm" from AlkOL
	 * @param slutTid      the time the visit ended, the same way
	 * @param tillsynsart  the code of the kind of inspection, Tillsynsform
	 * @param fritext      the note of the visit
	 * @param worddokument the document the system generated for the visit
	 * @param bifogadfil   the file attached to the visit
	 * @param handlaggare  the codes of the handlers who made the visit, Handl1 to Handl3, those that are not blank
	 * @param staff        the marks about the serving staff; AlkOL has none
	 * @param omgangsNr    the round of inspection the visit was made in, TillsynsOmgangsNr, null when none
	 * @param covered      what the inspection covered, such as tobacco or e-cigarettes, as texts; AlkT has none
	 * @param announced    true when the visit was announced in advance; AlkT has no such mark
	 */
	public record VisitRow(
		Integer tillfalleNr,
		Integer objektId,
		LocalDate datum,
		LocalDate slutDatum,
		String startTid,
		String slutTid,
		String tillsynsart,
		String fritext,
		String worddokument,
		String bifogadfil,
		List<String> handlaggare,
		StaffMarks staff,
		Integer omgangsNr,
		List<String> covered,
		boolean announced) {

		public VisitRow {
			handlaggare = handlaggare == null ? List.of() : List.copyOf(handlaggare);
			staff = staff == null ? StaffMarks.NONE : staff;
			covered = covered == null ? List.of() : List.copyOf(covered);
		}

		@Override
		public String toString() {
			return "VisitRow[tillfalleNr=" + tillfalleNr + "]";
		}
	}

	/**
	 * The marks AlkT keeps about the serving staff at a visit (decision 48), and whether someone of the staff was recorded
	 * at it (decision 80).
	 *
	 * @param ownerOnSite     ServPersAgarePaPlats, the owner was on site
	 * @param staffNotChecked ServPersEjKollat, the staff was not checked
	 * @param staffNotOnSite  ServPersEjpaPlats, the staff was not on site
	 * @param staffRecorded   someone of the serving staff was recorded at the visit, in Tillsyn_Servpers
	 */
	public record StaffMarks(boolean ownerOnSite, boolean staffNotChecked, boolean staffNotOnSite, boolean staffRecorded) {

		/**
		 * No marks.
		 */
		public static final StaffMarks NONE = new StaffMarks(false, false, false, false);

		/**
		 * @return the marks that are set, as the business names them
		 */
		public List<String> texts() {
			final var texts = new ArrayList<String>();
			if (ownerOnSite) {
				texts.add("Ägaren på plats");
			}
			if (staffNotChecked) {
				texts.add("Personalen inte kontrollerad");
			}
			if (staffNotOnSite) {
				texts.add("Personalen inte på plats");
			}
			if (staffRecorded) {
				texts.add("Personal registrerad vid besöket");
			}
			return texts;
		}

		/**
		 * @return whether the owner or someone responsible for the serving was on site, as the inspection protocol asks
		 *         (decision 80): true when the owner was on site or someone of the staff was recorded, false when only the
		 *         staff not being on site is marked, empty when the marks tell neither or contradict each other
		 */
		public Optional<Boolean> responsibleOnSite() {
			final var someoneOnSite = ownerOnSite || staffRecorded;
			return someoneOnSite == staffNotOnSite ? Optional.empty() : Optional.of(someoneOnSite);
		}
	}

	/**
	 * A row of Tillsyn_Omgång, a round of inspection that holds several visits, such as an evening of inspections
	 * together with the police.
	 *
	 * @param id      TillsynsOmgangsID
	 * @param namn    the name of the round, Omgangsnamn
	 * @param start   the first day of the round, null when unknown
	 * @param slut    the last day of the round, null when unknown
	 * @param fritext the note of the round, which concerns all its visits; null when blank
	 */
	public record RoundRow(Integer id, String namn, LocalDate start, LocalDate slut, String fritext) {

		@Override
		public String toString() {
			return "RoundRow[id=" + id + "]";
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
