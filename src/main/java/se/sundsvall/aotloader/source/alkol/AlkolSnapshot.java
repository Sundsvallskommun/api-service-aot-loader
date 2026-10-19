package se.sundsvall.aotloader.source.alkol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.InspectionVisitRows;
import se.sundsvall.aotloader.source.PersonRow;

/**
 * What the migration reads from AlkOL in one go. It holds personal data and is never logged or stored.
 *
 * @param places        the sales places
 * @param permitView    the current tobacco permits of the view FS_GT_Tobak, by FSID
 * @param owners        the owners by AgarID
 * @param errands       the errands
 * @param decisions     the decisions
 * @param events        the events
 * @param applicants    the applicants by SokandeID
 * @param persons       the active persons with significant influence, by AgarID
 * @param decisionTypes the texts of Beslutskoder
 * @param eventTypes    the texts of Händelsetyper
 * @param errandTypes   the texts of Ärendetyper
 * @param categories    the texts of Kategorier
 * @param areas         the texts of Områden
 * @param positions     the texts of PBI_Roller, the positions of the persons
 * @param visits        the inspection visits, with the texts of TillsynsArt and Anmarkningar, and the names of the
 *                      handlers by the number of the user (AnvID)
 * @param userNames     the names of the users by AnvändarKod, the code the decisions name who made them with
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
	Map<String, String> decisionTypes,
	Map<String, String> eventTypes,
	Map<String, String> errandTypes,
	Map<String, String> categories,
	Map<String, String> areas,
	Map<String, String> positions,
	InspectionVisitRows visits,
	Map<String, String> userNames) {

	/**
	 * A snapshot without the names of the users by their code.
	 */
	public AlkolSnapshot(final List<SalesPlaceRow> places, final Map<Integer, PermitViewRow> permitView, final Map<Integer, ContactRow> owners, final List<ErrandRow> errands,
		final List<DecisionRow> decisions, final List<EventRow> events, final Map<Integer, ContactRow> applicants, final Map<Integer, List<PersonRow>> persons,
		final Map<String, String> decisionTypes, final Map<String, String> eventTypes, final Map<String, String> errandTypes, final Map<String, String> categories,
		final Map<String, String> areas, final Map<String, String> positions, final InspectionVisitRows visits) {
		this(places, permitView, owners, errands, decisions, events, applicants, persons, decisionTypes, eventTypes, errandTypes, categories, areas, positions, visits, Map.of());
	}

	@Override
	public String toString() {
		return "AlkolSnapshot[places=" + places.size() + "]";
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
	 */
	public record PermitViewRow(Integer fsId, boolean retail, boolean wholesale, boolean distance) {
	}

	/**
	 * A row of Ärende. ObjektID is the FSID of the sales place.
	 *
	 * @param noteringar  the notes of the errand, null when blank
	 * @param handlaggare the handlers of the errand, HandlaggarID and HandlaggarID2, the numbers of the users (AnvID) as
	 *                    text, those that are set
	 * @param upplagd     the day the errand was registered, UpplagdDatum
	 */
	public record ErrandRow(Integer arendeId, Integer fsId, String arendetyp, String diarieNr, LocalDate oppnad, LocalDate avslutad, Integer sokandeId, String noteringar,
		List<String> handlaggare, LocalDate upplagd) {

		public ErrandRow {
			handlaggare = handlaggare == null ? List.of() : List.copyOf(handlaggare);
		}

		/**
		 * An errand without the day it was registered.
		 */
		public ErrandRow(final Integer arendeId, final Integer fsId, final String arendetyp, final String diarieNr, final LocalDate oppnad, final LocalDate avslutad,
			final Integer sokandeId, final String noteringar, final List<String> handlaggare) {
			this(arendeId, fsId, arendetyp, diarieNr, oppnad, avslutad, sokandeId, noteringar, handlaggare, null);
		}

		/**
		 * An errand without handlers.
		 */
		public ErrandRow(final Integer arendeId, final Integer fsId, final String arendetyp, final String diarieNr, final LocalDate oppnad, final LocalDate avslutad,
			final Integer sokandeId, final String noteringar) {
			this(arendeId, fsId, arendetyp, diarieNr, oppnad, avslutad, sokandeId, noteringar, List.of());
		}

		/**
		 * An errand without notes.
		 */
		public ErrandRow(final Integer arendeId, final Integer fsId, final String arendetyp, final String diarieNr, final LocalDate oppnad, final LocalDate avslutad,
			final Integer sokandeId) {
			this(arendeId, fsId, arendetyp, diarieNr, oppnad, avslutad, sokandeId, null);
		}

		@Override
		public String toString() {
			return "ErrandRow[arendeId=" + arendeId + ", fsId=" + fsId + ", arendetyp=" + arendetyp + "]";
		}
	}

	/**
	 * A row of Beslut.
	 *
	 * @param mall           the code of the document template the decision document was made from, null when none
	 * @param beslutsText    the text of the decision, null when blank
	 * @param beslutsfattare the code of who made the decision, BeslutsFattare, null when blank
	 */
	public record DecisionRow(Integer beslutsId, Integer arendeId, String beslutsTyp, LocalDateTime beslutad, LocalDate giltigtFrom, String mall, String worddokument,
		String bifogadfil, String beslutsText, String beslutsfattare) {

		/**
		 * A decision without a decision maker.
		 */
		public DecisionRow(final Integer beslutsId, final Integer arendeId, final String beslutsTyp, final LocalDateTime beslutad, final LocalDate giltigtFrom, final String mall,
			final String worddokument, final String bifogadfil, final String beslutsText) {
			this(beslutsId, arendeId, beslutsTyp, beslutad, giltigtFrom, mall, worddokument, bifogadfil, beslutsText, null);
		}

		/**
		 * A decision without a text.
		 */
		public DecisionRow(final Integer beslutsId, final Integer arendeId, final String beslutsTyp, final LocalDateTime beslutad, final LocalDate giltigtFrom, final String mall,
			final String worddokument, final String bifogadfil) {
			this(beslutsId, arendeId, beslutsTyp, beslutad, giltigtFrom, mall, worddokument, bifogadfil, null);
		}

		@Override
		public String toString() {
			return "DecisionRow[beslutsId=" + beslutsId + ", arendeId=" + arendeId + ", beslutsTyp=" + beslutsTyp + "]";
		}

		/**
		 * @return the date the decision was made, null when unknown
		 */
		public LocalDate beslutsDatum() {
			return beslutad == null ? null : beslutad.toLocalDate();
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

		/**
		 * An event that is not marked as confidential.
		 */
		public EventRow(final Integer handelseId, final Integer fsId, final Integer arendeId, final String handelseTyp, final LocalDateTime tidpunkt, final String diarieNr,
			final String mall, final String worddokument, final String bifogadfil, final String text) {
			this(handelseId, fsId, arendeId, handelseTyp, tidpunkt, diarieNr, mall, worddokument, bifogadfil, text, false);
		}

		/**
		 * An event without a text.
		 */
		public EventRow(final Integer handelseId, final Integer fsId, final Integer arendeId, final String handelseTyp, final LocalDateTime tidpunkt, final String diarieNr,
			final String mall, final String worddokument, final String bifogadfil) {
			this(handelseId, fsId, arendeId, handelseTyp, tidpunkt, diarieNr, mall, worddokument, bifogadfil, null);
		}

		@Override
		public String toString() {
			return "EventRow[handelseId=" + handelseId + ", fsId=" + fsId + ", arendeId=" + arendeId + ", handelseTyp=" + handelseTyp + "]";
		}
	}

}
