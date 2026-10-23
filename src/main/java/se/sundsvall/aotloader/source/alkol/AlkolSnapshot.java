package se.sundsvall.aotloader.source.alkol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import se.sundsvall.aotloader.source.CodeTexts;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.InspectionVisitRows;
import se.sundsvall.aotloader.source.PersonRow;
import se.sundsvall.aotloader.source.SourceDecision;
import se.sundsvall.aotloader.source.SourceErrand;

import static java.util.stream.Collectors.toMap;

/**
 * What the migration reads from AlkOL in one go. It holds personal data and is never logged or stored.
 *
 * @param places     the sales places
 * @param permitView the current tobacco permits of the view FS_GT_Tobak, by FSID
 * @param owners     the owners by AgarID
 * @param errands    the errands
 * @param decisions  the decisions
 * @param events     the events
 * @param applicants the applicants by SokandeID
 * @param persons    the active persons with significant influence, by AgarID
 * @param codes      the texts of the code lists
 * @param visits     the inspection visits, with the texts of TillsynsArt and Anmarkningar, and the names of the
 *                   handlers by the number of the user (AnvID)
 */
public record AlkolSnapshot(
	List<SalesPlaceRow> places,
	Map<Integer, PermitViewRow> permitView,
	Map<Integer, ContactRow> owners,
	List<ErrandRow> errands,
	List<DecisionRow> decisions,
	List<EventRow> events,
	Map<Integer, ContactRow> applicants,
	Map<Integer, List<PersonRow>> persons,
	CodeLists codes,
	InspectionVisitRows visits) {

	@Override
	public String toString() {
		return "AlkolSnapshot[places=" + places.size() + "]";
	}

	/**
	 * The texts of the code lists of AlkOL, by code. A code a list does not have is its own text.
	 *
	 * @param decisionTypes the texts of Beslutskoder
	 * @param eventTypes    the texts of Händelsetyper
	 * @param errandTypes   the texts of Ärendetyper
	 * @param categories    the texts of Kategorier
	 * @param areas         the texts of Områden
	 * @param positions     the texts of PBI_Roller, the positions of the persons
	 * @param userNames     the names of the users by AnvändarKod, the code the decisions name who made them with
	 */
	public record CodeLists(
		Map<String, String> decisionTypes,
		Map<String, String> eventTypes,
		Map<String, String> errandTypes,
		Map<String, String> categories,
		Map<String, String> areas,
		Map<String, String> positions,
		Map<String, String> userNames) {

		public String decisionType(final String code) {
			return CodeTexts.textOf(decisionTypes, code);
		}

		public String eventType(final String code) {
			return CodeTexts.textOf(eventTypes, code);
		}

		public String errandType(final String code) {
			return CodeTexts.textOf(errandTypes, code);
		}

		public String category(final String code) {
			return CodeTexts.textOf(categories, code);
		}

		public String area(final String code) {
			return CodeTexts.textOf(areas, code);
		}

		public String position(final String code) {
			return CodeTexts.textOf(positions, code);
		}

		public String userName(final String code) {
			return CodeTexts.textOf(userNames, code);
		}

		@Override
		public String toString() {
			return "CodeLists[userNames=" + userNames.size() + "]";
		}
	}

	/**
	 * A row of FörsäljningsStällen.
	 */
	public record SalesPlaceRow(
		Integer fsId,
		String namn,
		String gatuadress,
		String postnummer,
		String postort,
		String telefon,
		String email,
		String omrade,
		String kategori,
		String agareOrgNr,
		String notering,
		boolean folkol,
		boolean tobak,
		boolean ecigaretter,
		boolean tobaksfriNikotin,
		boolean upphort,
		Integer agarId) {

		@Override
		public String toString() {
			return "SalesPlaceRow[fsId=" + fsId + "]";
		}
	}

	/**
	 * A row of the view FS_GT_Tobak: the kinds of current tobacco permit of a sales place.
	 *
	 * @param unreadable true when a column of the row holds something else than a bit, whose kind is then not set
	 */
	public record PermitViewRow(Integer fsId, boolean retail, boolean wholesale, boolean distance, boolean unreadable) {

		/**
		 * @return the rows by sales place, in the order read; the rows of a place are merged, each kind set when any of
		 *         them sets it, and rows without a place are left out
		 */
		public static Map<Integer, PermitViewRow> bySalesPlace(final List<PermitViewRow> rows) {
			return rows.stream()
				.filter(row -> row.fsId() != null)
				.collect(toMap(PermitViewRow::fsId, row -> row, PermitViewRow::merge, LinkedHashMap::new));
		}

		private PermitViewRow merge(final PermitViewRow other) {
			return new PermitViewRow(fsId, retail || other.retail(), wholesale || other.wholesale(), distance || other.distance(), unreadable || other.unreadable());
		}
	}

	/**
	 * A row of Ärende. ObjektID is the FSID of the sales place.
	 *
	 * @param noteringar  the notes of the errand, null when blank
	 * @param handlaggare the handlers of the errand, HandlaggarID and HandlaggarID2, the numbers of the users (AnvID) as
	 *                    text, those that are set
	 * @param upplagd     the day the errand was registered, UpplagdDatum
	 */
	public record ErrandRow(Integer arendeId, Integer fsId, String arendeTyp, String diarieNr, LocalDate oppnad, LocalDate avslutad, Integer sokandeId, String noteringar,
		List<String> handlaggare, LocalDate upplagd)
		implements
		SourceErrand {

		public ErrandRow {
			handlaggare = handlaggare == null ? List.of() : List.copyOf(handlaggare);
		}

		@Override
		public String toString() {
			return "ErrandRow[arendeId=" + arendeId + ", fsId=" + fsId + ", arendeTyp=" + arendeTyp + "]";
		}
	}

	/**
	 * A row of Beslut.
	 *
	 * @param mall           the code of the document template the decision document was made from, null when none
	 * @param beslutsText    the text of the decision, null when blank
	 * @param beslutsfattare the code of who made the decision, BeslutsFattare, null when blank
	 */
	public record DecisionRow(Integer beslutsId, Integer arendeId, String beslutsTyp, LocalDateTime beslutad, LocalDate giltigtFrom, LocalDate giltigtTom, String mall, String worddokument,
		String bifogadfil, String beslutsText, String beslutsfattare)
		implements
		SourceDecision {

		@Override
		public String toString() {
			return "DecisionRow[beslutsId=" + beslutsId + ", arendeId=" + arendeId + ", beslutsTyp=" + beslutsTyp + "]";
		}
	}

	/**
	 * A row of Händelser.
	 *
	 * @param mall         the code of the document template the event's document was made from, null when none
	 * @param text         the text of the event, Handelsetext, null when blank
	 * @param confidential true when the event is marked as confidential, Sekretess
	 */
	public record EventRow(Integer handelseId, Integer fsId, Integer arendeId, String handelseTyp, LocalDateTime tidpunkt, String diarieNr, String mall, String worddokument,
		String bifogadfil, String text, boolean confidential) {

		@Override
		public String toString() {
			return "EventRow[handelseId=" + handelseId + ", fsId=" + fsId + ", arendeId=" + arendeId + ", handelseTyp=" + handelseTyp + "]";
		}
	}

}
