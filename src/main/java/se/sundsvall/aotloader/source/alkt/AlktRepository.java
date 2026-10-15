package se.sundsvall.aotloader.source.alkt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.FilePurposes;
import se.sundsvall.aotloader.source.InspectionVisitRows;
import se.sundsvall.aotloader.source.InspectionVisitRows.RemarkRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitFileRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.source.PartyRows;
import se.sundsvall.aotloader.source.PersonRow;
import se.sundsvall.aotloader.source.SourceDatabases;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ConditionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DocumentRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.FileRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PermitRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PlaceRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ServingTime;

import static java.util.stream.Collectors.groupingBy;
import static se.sundsvall.aotloader.service.model.Source.ALKT;
import static se.sundsvall.aotloader.source.Rows.byKey;
import static se.sundsvall.aotloader.source.Rows.date;
import static se.sundsvall.aotloader.source.Rows.flag;
import static se.sundsvall.aotloader.source.Rows.integer;
import static se.sundsvall.aotloader.source.Rows.text;

/**
 * Reads AlkT. The queries only select, and they use nothing that needs a compatibility level above 100.
 */
@Repository
public class AlktRepository {

	private static final int SERVING_TIME_INTERVALS = 8;
	private static final String ARENDE_ID = "ArendeID";
	private static final String OBJEKT_ID = "ObjektID";
	private static final String BIFOGADFIL = "Bifogadfil";
	private static final String HANDELSE_TYP = "HandelseTyp";
	private static final String HANDELSE_DATUM = "HandelseDatumTid";

	private static final String SELECT_PERMITS = """
		SELECT g.ObjektID, g.GTAgarID, g.Starkol, g.Vin, g.Spritdrycker, g.AJADrycker, g.ALP, g.AretRunt,
		       g.ArligPeriodStart, g.ArligPeriodSlut, g.PeriodStart, g.PeriodSlut,
		       g.Pausservering, g.Uteservering, g.Trafikservering, g.Minibar, g.Roomservice, g.Catering, g.Provsmakning,
		       g.Kryddning, g.Gardsforsaljning, g.ServeringAllmanhet, g.ServeringSlutetSallskap,
		       g.ServeringStartTid1, g.ServeringSlutTid1, g.ServeringStartTid2, g.ServeringSlutTid2,
		       g.ServeringStartTid3, g.ServeringSlutTid3, g.ServeringStartTid4, g.ServeringSlutTid4,
		       g.ServeringStartTid5, g.ServeringSlutTid5, g.ServeringStartTid6, g.ServeringSlutTid6,
		       g.ServeringStartTid7, g.ServeringSlutTid7, g.ServeringStartTid8, g.ServeringSlutTid8,
		       g.Hogstaantal, g.Sittplatser2, g.Serveringslokal,
		       g.Noteringar, g.Noteringar2, g.Noteringar3, g.Noteringar4, g.Noteringar5, g.Noteringar5B, g.Noteringar6,
		       g.Noteringar7, g.Noteringar8,
		       g.Ersatter_Datum, g.Ersatter_Diarienr, g.Utfardande_Datum, g.Utfardande_Diarienr, g.GTRestaurangBeteckning,
		       g.BeslutsID
		FROM dbo.[Gällande_Tillstånd] g
		""";

	private static final String SELECT_PLACES = """
		SELECT o.ObjektID, o.AgarID, o.RestaurangBeteckning, o.ObjektGrupp1, o.ServeringsNamn, o.ServeringsGAdress,
		       o.ServeringsPNr, o.ServeringsPOrt, o.TelefonNr1, o.Email, o.OmradesBeteckning, o.ObjBifogadfil, o.ObjBifogadfil2
		FROM dbo.Objekt o
		""";

	private static final String SELECT_OWNERS = """
		SELECT a.AgarID, a.OrganisationsNr, a.Bolagsnamn, a.KontaktPerson, a.GAdress, a.GAdress2, a.PNr, a.POrt,
		       a.TelefonNr1, a.EMail
		FROM dbo.[Ägare] a
		""";

	private static final String SELECT_CONDITIONS = """
		SELECT v.ObjID, v.Villkor, v.Notering, v.FR_OM, v.T_OM
		FROM dbo.ObjVillkor v
		WHERE v.ObjID IS NOT NULL
		""";

	private static final String SELECT_DECISIONS = """
		SELECT b.BeslutsID, b.ArendeID, b.BeslutsTyp, b.BeslutsDatumTid, b.GiltigtTOM_Datum, b.Dokument, b.Worddokument, b.BifogadFil
		FROM dbo.[Ärende_Beslut] b
		""";

	private static final String SELECT_ERRANDS = """
		SELECT ar.ArendeID, ar.ObjektID, ar.ArendeTyp, ar.DiarieNr, ar.OppnandeDatum, ar.AvslutsDatum, ar.SokandeID
		FROM dbo.[Ärende] ar
		""";

	// Also the events without an errand, whose files go to the permit of their place (decision 37)
	private static final String SELECT_EVENT_FILES = """
		SELECT h.HandelseID, h.ArendeID, h.ObjektID, h.HandelseTyp, h.Dokument, h.HandelseDatumTid, h.Worddokument, h.Bifogadfil
		FROM dbo.[Ärende_Händelser] h
		WHERE h.Worddokument IS NOT NULL OR h.Bifogadfil IS NOT NULL
		""";

	private static final String SELECT_EVENT_EXTRA_FILES = """
		SELECT h.HandelseID, h.ArendeID, h.ObjektID, h.HandelseTyp, h.HandelseDatumTid, f.Bifogadfil
		FROM dbo.[Ärende_Händelser_Filer] f
		JOIN dbo.[Ärende_Händelser] h ON h.HandelseID = f.HandelseID
		WHERE f.Bifogadfil IS NOT NULL
		""";

	private static final String SELECT_PICTURES = """
		SELECT bi.ArendeID, bi.Bildfilnamn
		FROM dbo.[Ärende_Bilder] bi
		WHERE bi.ArendeID IS NOT NULL
		  AND bi.Bildfilnamn IS NOT NULL
		""";

	private static final String SELECT_PERSONS = """
		SELECT p.AgarID, p.Namn, p.Personnr, p.Befattningskod1, p.Befattningskod2, p.Befattningskod3, p.UtlandskPerson
		FROM dbo.[PBI_ägare] p
		WHERE p.AgarID IS NOT NULL
		  AND (p.PASSIV IS NULL OR p.PASSIV = 0)
		""";

	private static final String SELECT_APPLICANTS = """
		SELECT s.SokandeID, s.OrganisationsNr, s.Bolagsnamn, s.KontaktPerson, s.GAdress, s.GAdress2, s.PNr, s.POrt,
		       s.TelefonNr1, s.EMail
		FROM dbo.[Sökande] s
		WHERE s.SokandeID IN (SELECT ar.SokandeID FROM dbo.[Ärende] ar WHERE ar.SokandeID IS NOT NULL)
		""";

	private static final String SELECT_CODE_TEXTS = """
		SELECT k.Kodtyp, k.Kod, k.Klartext
		FROM dbo.Klartext k
		""";

	private static final String SELECT_VISITS = """
		SELECT t.TillsynsTillfalleNr, t.ObjektId, t.StartDatum, t.StartTid, t.SlutTid, t.Tillsynsform, t.Fritext,
		       t.WordDokument, t.Bifogadfil
		FROM dbo.[Tillsyn_Tillfälle] t
		""";

	// Tillsyn_AnmSpec, the specifications of the remarks, is not read: every one of its texts is empty (analysis round 7b)
	private static final String SELECT_VISIT_REMARKS = """
		SELECT a.TillsynstillfalleNr, a.Anmarkningskod, a.Notering, a.Bifogadfil
		FROM dbo.[Tillsyn_Anmärkningar] a
		ORDER BY a.TillsynstillfalleNr, a.Anmarkningskod
		""";

	private static final String SELECT_VISIT_FILES = """
		SELECT f.TillsynsTillfalleNr, f.Bifogadfil
		FROM dbo.[Tillsyn_Tillfälle_Filer] f
		WHERE f.TillsynsTillfalleNr IS NOT NULL
		  AND f.Bifogadfil IS NOT NULL
		ORDER BY f.ID
		""";

	private final SourceDatabases sourceDatabases;

	AlktRepository(final SourceDatabases sourceDatabases) {
		this.sourceDatabases = sourceDatabases;
	}

	/**
	 * @return what the migration needs from AlkT
	 */
	public AlktSnapshot readSnapshot() {
		final var jdbc = sourceDatabases.jdbcTemplate(ALKT);
		final var errands = byKey(jdbc.query(SELECT_ERRANDS, (rs, _) -> toErrand(rs)), ErrandRow::arendeId);
		final var eventFiles = EventFiles.of(
			Stream.concat(
				jdbc.query(SELECT_EVENT_FILES, (rs, _) -> toEventFile(rs)).stream(),
				jdbc.query(SELECT_EVENT_EXTRA_FILES, (rs, _) -> toEventExtraFile(rs)).stream()).toList(),
			jdbc.query(SELECT_PICTURES, (rs, _) -> toPicture(rs)),
			errands.keySet());

		final var codeTexts = readCodeTexts(jdbc);
		return new AlktSnapshot(
			jdbc.query(SELECT_PERMITS, (rs, _) -> toPermit(rs)),
			byKey(jdbc.query(SELECT_PLACES, (rs, _) -> toPlace(rs)), PlaceRow::objektId),
			byKey(jdbc.query(SELECT_OWNERS, (rs, _) -> PartyRows.toOwner(rs)), ContactRow::id),
			jdbc.query(SELECT_CONDITIONS, (rs, _) -> toCondition(rs)).stream().filter(row -> row.objektId() != null).collect(groupingBy(ConditionRow::objektId)),
			byKey(jdbc.query(SELECT_DECISIONS, (rs, _) -> toDecision(rs)), DecisionRow::beslutsId),
			errands,
			eventFiles.errandFiles(),
			eventFiles.placeFiles(),
			eventFiles.unplacedFiles(),
			eventFiles.documents(),
			jdbc.query(SELECT_PERSONS, (rs, _) -> PartyRows.toPerson(rs)).stream().filter(row -> row.agarId() != null).collect(groupingBy(PersonRow::agarId)),
			byKey(jdbc.query(SELECT_APPLICANTS, (rs, _) -> PartyRows.toApplicant(rs)), ContactRow::id),
			codeTexts,
			readVisits(jdbc, codeTexts));
	}

	private static InspectionVisitRows readVisits(final JdbcTemplate jdbc, final Map<String, Map<String, String>> codeTexts) {
		return new InspectionVisitRows(
			jdbc.query(SELECT_VISITS, (rs, _) -> toVisit(rs)).stream().filter(row -> row.tillfalleNr() != null).toList(),
			jdbc.query(SELECT_VISIT_REMARKS, (rs, _) -> toRemark(rs)).stream().filter(row -> row.tillfalleNr() != null).collect(groupingBy(RemarkRow::tillfalleNr)),
			InspectionVisitRows.filesByVisit(jdbc.query(SELECT_VISIT_FILES, (rs, _) -> new VisitFileRow(integer(rs, "TillsynsTillfalleNr"), text(rs, BIFOGADFIL)))),
			codeTexts.getOrDefault(AlktSnapshot.CODE_TYPE_INSPECTION_KIND, Map.of()),
			codeTexts.getOrDefault(AlktSnapshot.CODE_TYPE_REMARK, Map.of()));
	}

	private static Map<String, Map<String, String>> readCodeTexts(final JdbcTemplate jdbc) {
		final var texts = new HashMap<String, Map<String, String>>();
		jdbc.query(SELECT_CODE_TEXTS, rs -> {
			final var codeType = text(rs, "Kodtyp");
			final var code = text(rs, "Kod");
			final var codeText = text(rs, "Klartext");
			if (codeType != null && code != null && codeText != null) {
				texts.computeIfAbsent(codeType, _ -> new HashMap<>()).put(code, codeText);
			}
		});
		return texts;
	}

	static PermitRow toPermit(final ResultSet rs) throws SQLException {
		final var servingTimes = new ArrayList<ServingTime>();
		for (var i = 1; i <= SERVING_TIME_INTERVALS; i++) {
			servingTimes.add(new ServingTime(text(rs, "ServeringStartTid" + i), text(rs, "ServeringSlutTid" + i)));
		}
		final var notes = new ArrayList<String>();
		for (final var column : List.of("Noteringar", "Noteringar2", "Noteringar3", "Noteringar4", "Noteringar5", "Noteringar5B", "Noteringar6", "Noteringar7", "Noteringar8")) {
			notes.add(text(rs, column));
		}
		return new PermitRow(
			integer(rs, OBJEKT_ID),
			integer(rs, "GTAgarID"),
			flag(rs, "Starkol"),
			flag(rs, "Vin"),
			flag(rs, "Spritdrycker"),
			flag(rs, "AJADrycker"),
			flag(rs, "ALP"),
			flag(rs, "AretRunt"),
			text(rs, "ArligPeriodStart"),
			text(rs, "ArligPeriodSlut"),
			text(rs, "PeriodStart"),
			text(rs, "PeriodSlut"),
			flag(rs, "Pausservering"),
			flag(rs, "Uteservering"),
			flag(rs, "Trafikservering"),
			flag(rs, "Minibar"),
			flag(rs, "Roomservice"),
			flag(rs, "Catering"),
			flag(rs, "Provsmakning"),
			flag(rs, "Kryddning"),
			flag(rs, "Gardsforsaljning"),
			flag(rs, "ServeringAllmanhet"),
			flag(rs, "ServeringSlutetSallskap"),
			servingTimes,
			text(rs, "Hogstaantal"),
			text(rs, "Sittplatser2"),
			text(rs, "Serveringslokal"),
			notes.stream().filter(Objects::nonNull).toList(),
			date(rs, "Ersatter_Datum"),
			text(rs, "Ersatter_Diarienr"),
			date(rs, "Utfardande_Datum"),
			text(rs, "Utfardande_Diarienr"),
			text(rs, "GTRestaurangBeteckning"),
			integer(rs, "BeslutsID"));
	}

	static PlaceRow toPlace(final ResultSet rs) throws SQLException {
		return new PlaceRow(
			integer(rs, OBJEKT_ID),
			integer(rs, "AgarID"),
			text(rs, "RestaurangBeteckning"),
			text(rs, "ObjektGrupp1"),
			text(rs, "ServeringsNamn"),
			text(rs, "ServeringsGAdress"),
			text(rs, "ServeringsPNr"),
			text(rs, "ServeringsPOrt"),
			text(rs, "TelefonNr1"),
			text(rs, "Email"),
			text(rs, "OmradesBeteckning"),
			text(rs, "ObjBifogadfil"),
			text(rs, "ObjBifogadfil2"));
	}

	static ConditionRow toCondition(final ResultSet rs) throws SQLException {
		return new ConditionRow(integer(rs, "ObjID"), text(rs, "Villkor"), text(rs, "Notering"), text(rs, "FR_OM"), text(rs, "T_OM"));
	}

	static DecisionRow toDecision(final ResultSet rs) throws SQLException {
		return new DecisionRow(
			integer(rs, "BeslutsID"),
			integer(rs, ARENDE_ID),
			text(rs, "BeslutsTyp"),
			date(rs, "BeslutsDatumTid"),
			date(rs, "GiltigtTOM_Datum"),
			text(rs, "Dokument"),
			text(rs, "Worddokument"),
			text(rs, "BifogadFil"));
	}

	static ErrandRow toErrand(final ResultSet rs) throws SQLException {
		return new ErrandRow(
			integer(rs, ARENDE_ID),
			integer(rs, OBJEKT_ID),
			text(rs, "ArendeTyp"),
			text(rs, "DiarieNr"),
			date(rs, "OppnandeDatum"),
			date(rs, "AvslutsDatum"),
			integer(rs, "SokandeID"));
	}

	static VisitRow toVisit(final ResultSet rs) throws SQLException {
		return new VisitRow(
			integer(rs, "TillsynsTillfalleNr"),
			integer(rs, "ObjektId"),
			date(rs, "StartDatum"),
			text(rs, "StartTid"),
			text(rs, "SlutTid"),
			text(rs, "Tillsynsform"),
			text(rs, "Fritext"),
			text(rs, "WordDokument"),
			text(rs, BIFOGADFIL));
	}

	static RemarkRow toRemark(final ResultSet rs) throws SQLException {
		return new RemarkRow(
			integer(rs, "TillsynstillfalleNr"),
			text(rs, "Anmarkningskod"),
			text(rs, "Notering"),
			text(rs, BIFOGADFIL));
	}

	static EventFile toEventFile(final ResultSet rs) throws SQLException {
		return new EventFile(
			integer(rs, "HandelseID"),
			integer(rs, ARENDE_ID),
			integer(rs, OBJEKT_ID),
			text(rs, HANDELSE_TYP),
			text(rs, "Dokument"),
			date(rs, HANDELSE_DATUM),
			text(rs, "Worddokument"),
			text(rs, BIFOGADFIL));
	}

	static EventFile toEventExtraFile(final ResultSet rs) throws SQLException {
		return new EventFile(integer(rs, "HandelseID"), integer(rs, ARENDE_ID), integer(rs, OBJEKT_ID), text(rs, HANDELSE_TYP), null, date(rs, HANDELSE_DATUM), null, text(rs, BIFOGADFIL));
	}

	/**
	 * The files of an event of AlkT, from the event itself or from Ärende_Händelser_Filer.
	 */
	record EventFile(Integer handelseId, Integer arendeId, Integer objektId, String handelseTyp, String mall, LocalDate datum, String worddokument, String bifogadfil) {

		/**
		 * @return the document and the attachment of the event, each with its purpose and the date of the event
		 */
		private Stream<FileRow> files(final Integer errand) {
			final var received = datum == null ? null : datum.atStartOfDay();
			return Stream.of(
				new FileRow(errand, worddokument, true, new FileDetails(FilePurposes.ofDocument(ALKT, mall), received)),
				new FileRow(errand, bifogadfil, false, new FileDetails(FilePurposes.ofAttachment(ALKT, handelseTyp, bifogadfil), received)))
				.filter(row -> row.name() != null);
		}
	}

	/**
	 * Reads a row of Ärende_Bilder, which has no date.
	 */
	static FileRow toPicture(final ResultSet rs) throws SQLException {
		final var name = text(rs, "Bildfilnamn");
		return new FileRow(integer(rs, ARENDE_ID), name, false, new FileDetails(FilePurposes.ofAttachment(ALKT, null, name), null));
	}

	/**
	 * The files of the events and pictures, split by where they go. An event whose errand is missing in Ärende counts as
	 * one without an errand.
	 *
	 * @param errandFiles   the files of the events with an errand, and the pictures, by ArendeID
	 * @param placeFiles    the files of the events without an errand, by the ObjektID of the event
	 * @param unplacedFiles the files of the events with neither an errand nor a place, by HandelseID
	 * @param documents     the documents of every event, with their template
	 */
	record EventFiles(Map<Integer, List<FileRow>> errandFiles, Map<Integer, List<FileRow>> placeFiles, Map<Integer, List<FileRow>> unplacedFiles, List<DocumentRow> documents) {

		static EventFiles of(final List<EventFile> events, final List<FileRow> pictures, final Set<Integer> errandIds) {
			final Predicate<EventFile> withErrand = event -> event.arendeId() != null && errandIds.contains(event.arendeId());
			final var errandFiles = Stream.concat(
				events.stream().filter(withErrand).flatMap(event -> event.files(event.arendeId())),
				pictures.stream().filter(row -> row.name() != null && row.arendeId() != null))
				.collect(groupingBy(FileRow::arendeId));
			final var placeFiles = new HashMap<Integer, List<FileRow>>();
			final var unplacedFiles = new HashMap<Integer, List<FileRow>>();
			events.stream()
				.filter(withErrand.negate())
				.forEach(event -> event.files(null).forEach(row -> (event.objektId() == null
					? unplacedFiles.computeIfAbsent(event.handelseId(), _ -> new ArrayList<>())
					: placeFiles.computeIfAbsent(event.objektId(), _ -> new ArrayList<>())).add(row)));
			final var documents = events.stream()
				.filter(event -> event.worddokument() != null)
				.map(event -> new DocumentRow(event.handelseId(), event.arendeId(), event.objektId(), event.mall(), event.datum(), event.worddokument()))
				.toList();
			return new EventFiles(errandFiles, placeFiles, unplacedFiles, documents);
		}
	}
}
