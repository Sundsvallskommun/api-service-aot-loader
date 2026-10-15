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
 * @param errandTypes   the texts of Ärendetyper
 * @param categories    the texts of Kategorier
 * @param areas         the texts of Områden
 * @param positions     the texts of PBI_Roller, the positions of the persons
 * @param visits        the inspection visits, with the texts of TillsynsArt and Anmarkningar
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
	Map<String, String> errandTypes,
	Map<String, String> categories,
	Map<String, String> areas,
	Map<String, String> positions,
	InspectionVisitRows visits) {

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
	 */
	public record ErrandRow(Integer arendeId, Integer fsId, String arendetyp, String diarieNr, LocalDate oppnad, LocalDate avslutad, Integer sokandeId) {
	}

	/**
	 * A row of Beslut.
	 *
	 * @param mall the code of the document template the decision document was made from, null when none
	 */
	public record DecisionRow(Integer beslutsId, Integer arendeId, String beslutsTyp, LocalDateTime beslutad, LocalDate giltigtFrom, String mall, String worddokument,
		String bifogadfil) {

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
	 * @param mall the code of the document template the event's document was made from, null when none
	 */
	public record EventRow(Integer handelseId, Integer fsId, Integer arendeId, String handelseTyp, LocalDateTime tidpunkt, String diarieNr, String mall, String worddokument,
		String bifogadfil) {
	}

}
