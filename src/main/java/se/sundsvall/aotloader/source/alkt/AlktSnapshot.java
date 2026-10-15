package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.source.CodeTexts;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.InspectionVisitRows;
import se.sundsvall.aotloader.source.PersonRow;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

/**
 * What the migration reads from AlkT in one go: the tables it needs, in memory, so that rules that compare permits with
 * each other see all of them. It holds personal data and is never logged or stored.
 *
 * @param permits       the rows of Gällande_Tillstånd
 * @param places        the serving places by ObjektID
 * @param owners        the owners by AgarID
 * @param conditions    the conditions by ObjektID
 * @param decisions     the decisions by BeslutsID
 * @param errands       the errands by ArendeID
 * @param errandFiles   the files of the errands' events and pictures, by ArendeID
 * @param placeFiles    the files of the events without an errand, by the ObjektID of the event; an event whose errand
 *                      is
 *                      missing counts as one without an errand
 * @param unplacedFiles the files of the events with neither an errand nor a place, by HandelseID; they go nowhere but
 *                      tell which file names are shared
 * @param documents     the documents AlkT generated at events, with the template each was made from
 * @param persons       the active persons with significant influence, by AgarID
 * @param applicants    the applicants of the inspection errands, by SokandeID
 * @param codeTexts     the texts of the code lists, by code type and code
 * @param visits        the inspection visits, with the code lists of kinds and remarks (code types L and V)
 */
public record AlktSnapshot(
	List<PermitRow> permits,
	Map<Integer, PlaceRow> places,
	Map<Integer, ContactRow> owners,
	Map<Integer, List<ConditionRow>> conditions,
	Map<Integer, DecisionRow> decisions,
	Map<Integer, ErrandRow> errands,
	Map<Integer, List<FileRow>> errandFiles,
	Map<Integer, List<FileRow>> placeFiles,
	Map<Integer, List<FileRow>> unplacedFiles,
	List<DocumentRow> documents,
	Map<Integer, List<PersonRow>> persons,
	Map<Integer, ContactRow> applicants,
	Map<String, Map<String, String>> codeTexts,
	InspectionVisitRows visits) {

	public static final String CODE_TYPE_ERRAND = "A";
	public static final String CODE_TYPE_DECISION = "B";
	public static final String CODE_TYPE_AREA = "G";
	public static final String CODE_TYPE_INSPECTION_KIND = "L";
	public static final String CODE_TYPE_CONDITION = "O";
	public static final String CODE_TYPE_POSITION = "P";
	public static final String CODE_TYPE_PLACE_GROUP = "Q";
	public static final String CODE_TYPE_REMARK = "V";

	@Override
	public String toString() {
		return "AlktSnapshot[permits=" + permits.size() + "]";
	}

	/**
	 * @return the text of the code, or the code itself when the code list does not have it; null for a null code
	 */
	public String codeText(final String codeType, final String code) {
		return CodeTexts.textOf(codeTexts.getOrDefault(codeType, emptyMap()), code);
	}

	public List<ConditionRow> conditionsOf(final Integer objektId) {
		return conditions.getOrDefault(objektId, emptyList());
	}

	public List<FileRow> filesOf(final Integer arendeId) {
		return errandFiles.getOrDefault(arendeId, emptyList());
	}

	/**
	 * @return the files of the events of the place that have no errand
	 */
	public List<FileRow> placeFilesOf(final Integer objektId) {
		return placeFiles.getOrDefault(objektId, emptyList());
	}

	public List<PersonRow> personsOf(final Integer agarId) {
		return persons.getOrDefault(agarId, emptyList());
	}

	/**
	 * A row of Gällande_Tillstånd. The serving times hold the eight intervals in order, start and end.
	 */
	public record PermitRow(
		Integer objektId,
		Integer gtAgarId,
		boolean starkol,
		boolean vin,
		boolean spritdrycker,
		boolean ajaDrycker,
		boolean alp,
		boolean aretRunt,
		String arligPeriodStart,
		String arligPeriodSlut,
		String periodStart,
		String periodSlut,
		boolean pausservering,
		boolean uteservering,
		boolean trafikservering,
		boolean minibar,
		boolean roomservice,
		boolean catering,
		boolean provsmakning,
		boolean kryddning,
		boolean gardsforsaljning,
		boolean servingPublic,
		boolean servingPrivate,
		List<ServingTime> servingTimes,
		String hogstaAntal,
		String sittplatser,
		String serveringslokal,
		List<String> notes,
		LocalDate ersatterDatum,
		String ersatterDiarienr,
		LocalDate utfardandeDatum,
		String utfardandeDiarienr,
		String gtRestaurangBeteckning,
		Integer beslutsId) {
	}

	/**
	 * A serving time interval as the source has it.
	 */
	public record ServingTime(String start, String end) {
	}

	/**
	 * A row of Objekt.
	 */
	public record PlaceRow(
		Integer objektId,
		Integer agarId,
		String restaurangBeteckning,
		String objektGrupp1,
		String namn,
		String adress,
		String postnummer,
		String postort,
		String telefon,
		String email,
		String omrade,
		String bifogadfil,
		String bifogadfil2) {
	}

	/**
	 * A row of ObjVillkor.
	 */
	public record ConditionRow(Integer objektId, String code, String text, String validFrom, String validTo) {
	}

	/**
	 * A row of Ärende_Beslut.
	 *
	 * @param beslutad when the decision was made, null when unknown
	 * @param mall     the code of the document template the decision document was made from, null when none
	 */
	public record DecisionRow(
		Integer beslutsId,
		Integer arendeId,
		String beslutsTyp,
		LocalDateTime beslutad,
		LocalDate giltigtTom,
		String mall,
		String worddokument,
		String bifogadFil) {

		/**
		 * @return the date the decision was made, null when unknown
		 */
		public LocalDate beslutsDatum() {
			return beslutad == null ? null : beslutad.toLocalDate();
		}
	}

	/**
	 * A row of Ärende.
	 */
	public record ErrandRow(
		Integer arendeId,
		Integer objektId,
		String arendeTyp,
		String diarieNr,
		LocalDate oppnad,
		LocalDate avslutad,
		Integer sokandeId) {
	}

	/**
	 * A file reference of an errand, from its events or pictures.
	 *
	 * @param arendeId  the errand
	 * @param name      the file name
	 * @param generated true for a document AlkT generated, false for an attachment
	 * @param details   the purpose and date the attachment of the file gets on the errand
	 */
	public record FileRow(Integer arendeId, String name, boolean generated, FileDetails details) {

		public FileRow {
			details = details == null ? FileDetails.NONE : details;
		}

		/**
		 * A file without a purpose or a date.
		 */
		public FileRow(final Integer arendeId, final String name, final boolean generated) {
			this(arendeId, name, generated, FileDetails.NONE);
		}
	}

	/**
	 * A document AlkT generated at an event.
	 *
	 * @param handelseId the event
	 * @param arendeId   the errand of the event, null when it has none
	 * @param objektId   the place of the event itself, which an event without an errand belongs to
	 * @param mall       the code of the document template, null when none
	 * @param tidpunkt   when the event was, null when unknown
	 * @param name       the file name
	 */
	public record DocumentRow(Integer handelseId, Integer arendeId, Integer objektId, String mall, LocalDateTime tidpunkt, String name) {
	}

}
