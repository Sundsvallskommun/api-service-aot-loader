package se.sundsvall.aotloader.source.alkt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import se.sundsvall.aotloader.source.InspectionVisitRows.RoundRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.StaffMarks;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitFileRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.source.PartyRows;
import se.sundsvall.aotloader.source.PersonRow;
import se.sundsvall.aotloader.source.SourceDatabases;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ConditionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DocumentRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.Drinks;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.FileRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.Period;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PermitRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PlaceEventRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PlaceRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.Scope;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ServingArea;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ServingTime;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.TextRow;
import se.sundsvall.aotloader.support.Texts;

import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static se.sundsvall.aotloader.service.model.Source.ALKT;
import static se.sundsvall.aotloader.source.Rows.byKey;
import static se.sundsvall.aotloader.source.Rows.date;
import static se.sundsvall.aotloader.source.Rows.dateTime;
import static se.sundsvall.aotloader.source.Rows.flag;
import static se.sundsvall.aotloader.source.Rows.integer;
import static se.sundsvall.aotloader.source.Rows.text;
import static se.sundsvall.aotloader.source.Rows.texts;

/**
 * Reads AlkT. The queries only select, and they use nothing that needs a compatibility level above 100.
 */
@Repository
public class AlktRepository {

	private static final int SERVING_TIME_INTERVALS = 8;
	/**
	 * The text of each serving time interval, in the order of the intervals (analysis round 14c). Noteringar5 is the
	 * general note of the permit; the texts of interval 5-8 are filled nowhere, and theirs follow the same order.
	 */
	private static final List<String> SERVING_TIME_TEXTS = List.of("Noteringar", "Noteringar2", "Noteringar3", "Noteringar4", "Noteringar5B", "Noteringar6", "Noteringar7",
		"Noteringar8");
	/** Noteringar5, the general note, comes after the text of this interval, as the columns come. */
	private static final int GENERAL_NOTE_AFTER_INTERVAL = 4;
	private static final String ARENDE_ID = "ArendeID";
	private static final String OBJEKT_ID = "ObjektID";
	private static final String BIFOGADFIL = "Bifogadfil";
	private static final String HANDELSE_TYP = "HandelseTyp";
	private static final String HANDELSE_DATUM = "HandelseDatumTid";
	private static final String NO_INSPECTION_KIND = "01";

	// The table has no key: the order numbers the permits without ObjektID the same way in every run, as long as their
	// rows differ in the columns of the order
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
		ORDER BY g.ObjektID, g.Utfardande_Diarienr, g.Utfardande_Datum, g.BeslutsID
		""";

	private static final String SELECT_PLACES = """
		SELECT o.ObjektID, o.AgarID, o.RestaurangBeteckning, o.ObjektGrupp1, o.ServeringsNamn, o.ServeringsGAdress,
		       o.ServeringsPNr, o.ServeringsPOrt, o.TelefonNr1, o.Email, o.OmradesBeteckning, o.ObjBifogadfil, o.ObjBifogadfil2
		FROM dbo.Objekt o
		ORDER BY o.ObjektID
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
		ORDER BY v.ObjID, v.ID
		""";

	// Ordered, like the errands, so that decisions at the same time keep their order in the journal from run to run
	private static final String SELECT_DECISIONS = """
		SELECT b.BeslutsID, b.ArendeID, b.BeslutsTyp, b.BeslutsDatumTid, b.GiltigtFROM_Datum, b.GiltigtTOM_Datum, b.Dokument, b.Worddokument, b.BifogadFil,
		       b.BeslutsText, b.BeslutsText2, b.BeslutsFattare
		FROM dbo.[Ärende_Beslut] b
		ORDER BY b.BeslutsID
		""";

	private static final String SELECT_ERRANDS = """
		SELECT ar.ArendeID, ar.ObjektID, ar.ArendeTyp, ar.DiarieNr, ar.OppnandeDatum, ar.AvslutsDatum, ar.SokandeID,
		       ar.Noteringar, ar.Noteringar2, ar.Noteringar3, ar.Noteringar4, ar.Noteringar5, ar.Noteringar5B, ar.Noteringar6,
		       ar.Noteringar7, ar.Noteringar8, ar.ArendeNotat, ar.HandlaggarID, ar.HandlaggarID2, ar.UpplagdDatum
		FROM dbo.[Ärende] ar
		ORDER BY ar.ArendeID
		""";

	// Every event, also one without a text, since an errand's events make the journal of its investigation (decision 52).
	// Ordered so that events at the same time keep the order AlkT gave them
	private static final String SELECT_EVENT_TEXTS = """
		SELECT h.HandelseID, h.ArendeID, h.ObjektID, h.HandelseTyp, h.HandelseDatumTid, h.HandelseText, h.Sekretess, h.DiarieNr
		FROM dbo.[Ärende_Händelser] h
		ORDER BY h.HandelseID
		""";

	private static final String SELECT_PICTURE_TEXTS = """
		SELECT bi.ArendeID, bi.BildDatumTid, bi.BildText
		FROM dbo.[Ärende_Bilder] bi
		WHERE bi.ArendeID IS NOT NULL
		  AND bi.BildText IS NOT NULL
		ORDER BY bi.BildID
		""";

	// Also the events without an errand, whose files go to their documentation errands (decision 71). Ordered, as the
	// other file queries, since a file on two events keeps the date and purpose of the first from run to run
	private static final String SELECT_EVENT_FILES = """
		SELECT h.HandelseID, h.ArendeID, h.ObjektID, h.HandelseTyp, h.Dokument, h.HandelseDatumTid, h.Worddokument, h.Bifogadfil
		FROM dbo.[Ärende_Händelser] h
		WHERE h.Worddokument IS NOT NULL OR h.Bifogadfil IS NOT NULL
		ORDER BY h.HandelseID
		""";

	private static final String SELECT_EVENT_EXTRA_FILES = """
		SELECT h.HandelseID, h.ArendeID, h.ObjektID, h.HandelseTyp, h.HandelseDatumTid, f.Bifogadfil
		FROM dbo.[Ärende_Händelser_Filer] f
		JOIN dbo.[Ärende_Händelser] h ON h.HandelseID = f.HandelseID
		WHERE f.Bifogadfil IS NOT NULL
		ORDER BY f.ID
		""";

	private static final String SELECT_PICTURES = """
		SELECT bi.ArendeID, bi.Bildfilnamn, bi.BildDatumTid
		FROM dbo.[Ärende_Bilder] bi
		WHERE bi.ArendeID IS NOT NULL
		  AND bi.Bildfilnamn IS NOT NULL
		ORDER BY bi.BildID
		""";

	private static final String SELECT_PERSONS = """
		SELECT p.AgarID, p.Namn, p.Personnr, p.Befattningskod1, p.Befattningskod2, p.Befattningskod3, p.UtlandskPerson
		FROM dbo.[PBI_ägare] p
		WHERE p.AgarID IS NOT NULL
		  AND (p.PASSIV IS NULL OR p.PASSIV = 0)
		ORDER BY p.AgarID, p.PBIAGID
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
		SELECT t.TillsynsTillfalleNr, t.ObjektId, t.StartDatum, t.SlutDatum, t.StartTid, t.SlutTid, t.Tillsynsform, t.Fritext,
		       t.WordDokument, t.Bifogadfil, t.Handl1, t.Handl2, t.Handl3, t.ServPersAgarePaPlats, t.ServPersEjKollat, t.ServPersEjpaPlats,
		       t.TillsynsOmgangsNr
		FROM dbo.[Tillsyn_Tillfälle] t
		ORDER BY t.TillsynsTillfalleNr
		""";

	private static final String SELECT_ROUNDS = """
		SELECT o.TillsynsOmgangsID, o.Omgangsnamn, o.StartDatum, o.SlutDatum, o.Fritext
		FROM dbo.[Tillsyn_Omgång] o
		ORDER BY o.TillsynsOmgangsID
		""";

	private static final String SELECT_HANDLERS = """
		SELECT h.HandlaggarID, h.Namn
		FROM dbo.[Handläggare] h
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
	 * @return what the migration needs from AlkT, read in one snapshot of the database when it allows one
	 */
	public AlktSnapshot readSnapshot() {
		return sourceDatabases.read(ALKT, AlktRepository::snapshotOf);
	}

	private static AlktSnapshot snapshotOf(final JdbcTemplate jdbc) {
		final var errands = byKey(jdbc.query(SELECT_ERRANDS, (rs, _) -> toErrand(rs)), ErrandRow::arendeId);
		final var files = Stream.concat(
			jdbc.query(SELECT_EVENT_FILES, (rs, _) -> toEventFile(rs)).stream(),
			jdbc.query(SELECT_EVENT_EXTRA_FILES, (rs, _) -> toEventExtraFile(rs)).stream()).toList();
		final var eventFiles = EventFiles.of(files, jdbc.query(SELECT_PICTURES, (rs, _) -> toPicture(rs)), errands.keySet());

		final var events = jdbc.query(SELECT_EVENT_TEXTS, (rs, _) -> toEventText(rs));
		final var eventTexts = EventTexts.of(events, jdbc.query(SELECT_PICTURE_TEXTS, (rs, _) -> toPictureText(rs)), errands.keySet());

		final var codeTexts = readCodeTexts(jdbc);
		return new AlktSnapshot(
			jdbc.query(SELECT_PERMITS, (rs, _) -> toPermit(rs)),
			byKey(jdbc.query(SELECT_PLACES, (rs, _) -> toPlace(rs)), PlaceRow::objektId),
			byKey(jdbc.query(SELECT_OWNERS, (rs, _) -> PartyRows.toOwner(rs)), ContactRow::id),
			jdbc.query(SELECT_CONDITIONS, (rs, _) -> toCondition(rs)).stream().filter(row -> row.objektId() != null).collect(groupingBy(ConditionRow::objektId)),
			byKey(jdbc.query(SELECT_DECISIONS, (rs, _) -> toDecision(rs)), DecisionRow::beslutsId),
			errands,
			eventFiles.errandFiles(),
			placeEvents(events, files, errands.keySet()),
			eventFiles.unplacedFiles(),
			eventFiles.documents(),
			eventTexts.errandTexts(),
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
			codeTexts.getOrDefault(AlktSnapshot.CODE_TYPE_REMARK, Map.of()),
			readHandlerNames(jdbc),
			byKey(jdbc.query(SELECT_ROUNDS, (rs, _) -> InspectionVisitRows.toRound(rs)), RoundRow::id));
	}

	/**
	 * @return the names of the handlers by HandlaggarID, the code the visits name them with (decision 48)
	 */
	private static Map<String, String> readHandlerNames(final JdbcTemplate jdbc) {
		final var names = new HashMap<String, String>();
		jdbc.query(SELECT_HANDLERS, rs -> {
			final var code = text(rs, "HandlaggarID");
			final var name = text(rs, "Namn");
			if (code != null && name != null) {
				names.putIfAbsent(code, name);
			}
		});
		return names;
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
		final var notes = new ArrayList<String>();
		for (var i = 1; i <= SERVING_TIME_INTERVALS; i++) {
			final var time = new ServingTime(text(rs, "ServeringStartTid" + i), text(rs, "ServeringSlutTid" + i), text(rs, SERVING_TIME_TEXTS.get(i - 1)));
			servingTimes.add(time);
			// The text of an interval without a time is no serving time, and is kept as a note
			if (!time.isSet()) {
				notes.add(time.text());
			}
			if (i == GENERAL_NOTE_AFTER_INTERVAL) {
				notes.add(text(rs, "Noteringar5"));
			}
		}
		return new PermitRow(
			integer(rs, OBJEKT_ID),
			integer(rs, "GTAgarID"),
			new Drinks(flag(rs, "Starkol"), flag(rs, "Vin"), flag(rs, "Spritdrycker"), flag(rs, "AJADrycker"), flag(rs, "ALP")),
			new Period(flag(rs, "AretRunt"), text(rs, "ArligPeriodStart"), text(rs, "ArligPeriodSlut"), text(rs, "PeriodStart"), text(rs, "PeriodSlut")),
			new Scope(flag(rs, "Pausservering"), flag(rs, "Uteservering"), flag(rs, "Trafikservering"), flag(rs, "Minibar"), flag(rs, "Roomservice"), flag(rs, "Catering"), flag(rs, "Provsmakning"),
				flag(rs, "Kryddning"), flag(rs, "Gardsforsaljning")),
			flag(rs, "ServeringAllmanhet"),
			flag(rs, "ServeringSlutetSallskap"),
			servingTimes,
			new ServingArea(text(rs, "Hogstaantal"), text(rs, "Sittplatser2"), text(rs, "Serveringslokal")),
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
			dateTime(rs, "BeslutsDatumTid"),
			date(rs, "GiltigtFROM_Datum"),
			date(rs, "GiltigtTOM_Datum"),
			text(rs, "Dokument"),
			text(rs, "Worddokument"),
			text(rs, "BifogadFil"),
			Texts.join("\n", Arrays.asList(text(rs, "BeslutsText"), text(rs, "BeslutsText2"))),
			text(rs, "BeslutsFattare"));
	}

	static ErrandRow toErrand(final ResultSet rs) throws SQLException {
		return new ErrandRow(
			integer(rs, ARENDE_ID),
			integer(rs, OBJEKT_ID),
			text(rs, "ArendeTyp"),
			text(rs, "DiarieNr"),
			date(rs, "OppnandeDatum"),
			date(rs, "AvslutsDatum"),
			integer(rs, "SokandeID"),
			texts(rs, "Noteringar", "Noteringar2", "Noteringar3", "Noteringar4", "Noteringar5", "Noteringar5B", "Noteringar6", "Noteringar7", "Noteringar8", "ArendeNotat"),
			texts(rs, "HandlaggarID", "HandlaggarID2"),
			date(rs, "UpplagdDatum"));
	}

	static VisitRow toVisit(final ResultSet rs) throws SQLException {
		return new VisitRow(
			integer(rs, "TillsynsTillfalleNr"),
			integer(rs, "ObjektId"),
			date(rs, "StartDatum"),
			date(rs, "SlutDatum"),
			text(rs, "StartTid"),
			text(rs, "SlutTid"),
			inspectionKind(rs),
			text(rs, "Fritext"),
			text(rs, "WordDokument"),
			text(rs, BIFOGADFIL),
			texts(rs, "Handl1", "Handl2", "Handl3"),
			new StaffMarks(flag(rs, "ServPersAgarePaPlats"), flag(rs, "ServPersEjKollat"), flag(rs, "ServPersEjpaPlats")),
			integer(rs, "TillsynsOmgangsNr"),
			List.of(),
			false);
	}

	/**
	 * AlkT writes 01 in Tillsynsform when no kind of inspection was chosen, a code that code list L does not have. It
	 * counts as no kind, the same as a blank one (decision 42).
	 */
	static String inspectionKind(final ResultSet rs) throws SQLException {
		final var code = text(rs, "Tillsynsform");
		return NO_INSPECTION_KIND.equals(code) ? null : code;
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
			dateTime(rs, HANDELSE_DATUM),
			text(rs, "Worddokument"),
			text(rs, BIFOGADFIL));
	}

	static EventFile toEventExtraFile(final ResultSet rs) throws SQLException {
		return new EventFile(integer(rs, "HandelseID"), integer(rs, ARENDE_ID), integer(rs, OBJEKT_ID), text(rs, HANDELSE_TYP), null, dateTime(rs, HANDELSE_DATUM), null, text(rs, BIFOGADFIL));
	}

	/**
	 * The files of an event of AlkT, from the event itself or from Ärende_Händelser_Filer.
	 */
	record EventFile(Integer handelseId, Integer arendeId, Integer objektId, String handelseTyp, String mall, LocalDateTime tidpunkt, String worddokument, String bifogadfil) {

		/**
		 * @return the document and the attachment of the event, each with its purpose and the time of the event
		 */
		private Stream<FileRow> files(final Integer errand) {
			return Stream.of(
				new FileRow(errand, worddokument, true, FilePurposes.ofGenerated(ALKT, mall, handelseTyp, tidpunkt)),
				new FileRow(errand, bifogadfil, false, FilePurposes.ofAttached(ALKT, handelseTyp, bifogadfil, tidpunkt)))
				.filter(row -> row.name() != null);
		}
	}

	static EventText toEventText(final ResultSet rs) throws SQLException {
		return new EventText(integer(rs, "HandelseID"), integer(rs, ARENDE_ID), integer(rs, OBJEKT_ID),
			new TextRow(dateTime(rs, HANDELSE_DATUM), text(rs, HANDELSE_TYP), false, text(rs, "HandelseText"), flag(rs, "Sekretess"), text(rs, "DiarieNr")));
	}

	static EventText toPictureText(final ResultSet rs) throws SQLException {
		return new EventText(null, integer(rs, ARENDE_ID), null, new TextRow(dateTime(rs, "BildDatumTid"), null, true, text(rs, "BildText"), false, null));
	}

	/**
	 * The text of an event or a picture with where it belongs: its errand, or the place of an event without one.
	 *
	 * @param handelseId the event, null for a picture
	 */
	record EventText(Integer handelseId, Integer arendeId, Integer objektId, TextRow row) {
	}

	/**
	 * The events of the errands and the texts of their pictures (decisions 44 and 52). Every event of an errand counts,
	 * also one without a text, but a picture only with a text. An event whose errand is missing in Ärende counts as one
	 * without an errand, which {@link #placeEvents} takes.
	 *
	 * @param errandTexts the events with an errand, and the texts of the pictures, by ArendeID
	 */
	record EventTexts(Map<Integer, List<TextRow>> errandTexts) {

		static EventTexts of(final List<EventText> events, final List<EventText> pictures, final Set<Integer> errandIds) {
			final var errandTexts = new HashMap<Integer, List<TextRow>>();
			Stream.concat(events.stream(), pictures.stream())
				.filter(text -> text.arendeId() != null && errandIds.contains(text.arendeId()))
				.filter(text -> !text.row().picture() || text.row().text() != null)
				.forEach(text -> errandTexts.computeIfAbsent(text.arendeId(), _ -> new ArrayList<>()).add(text.row()));
			return new EventTexts(errandTexts);
		}
	}

	/**
	 * The events of the places that belong to no errand, each with its text and its files, which become documentation
	 * errands (decision 71). An event whose errand is missing in Ärende counts as one without an errand; an event without
	 * a place goes nowhere.
	 *
	 * @param  events    every event, with its text
	 * @param  files     the files of the events
	 * @param  errandIds the errands in Ärende
	 * @return           the events by the ObjektID of the event, each place's in the order of their ids
	 */
	static Map<Integer, List<PlaceEventRow>> placeEvents(final List<EventText> events, final List<EventFile> files, final Set<Integer> errandIds) {
		final Predicate<Integer> withoutErrand = arendeId -> arendeId == null || !errandIds.contains(arendeId);
		final var filesOfEvents = files.stream()
			.filter(file -> withoutErrand.test(file.arendeId()) && file.objektId() != null)
			.collect(groupingBy(EventFile::handelseId, LinkedHashMap::new, flatMapping(file -> file.files(null), toList())));
		final var placeEvents = new HashMap<Integer, List<PlaceEventRow>>();
		events.stream()
			.filter(event -> event.handelseId() != null && event.objektId() != null && withoutErrand.test(event.arendeId()))
			.sorted(Comparator.comparing(EventText::handelseId))
			.forEach(event -> placeEvents.computeIfAbsent(event.objektId(), _ -> new ArrayList<>())
				.add(new PlaceEventRow(event.handelseId(), event.row(), filesOfEvents.getOrDefault(event.handelseId(), List.of()))));
		return placeEvents;
	}

	static FileRow toPicture(final ResultSet rs) throws SQLException {
		final var name = text(rs, "Bildfilnamn");
		return new FileRow(integer(rs, ARENDE_ID), name, false, new FileDetails(FilePurposes.ofAttachment(ALKT, null, name), dateTime(rs, "BildDatumTid")));
	}

	/**
	 * The files of the events and pictures, split by where they go. An event whose errand is missing in Ärende counts as
	 * one without an errand.
	 *
	 * @param errandFiles   the files of the events with an errand, and the pictures, by ArendeID
	 * @param unplacedFiles the files of the events with neither an errand nor a place, by HandelseID; the files of the
	 *                      events with a place but no errand are {@link #placeEvents}'
	 * @param documents     the documents of every event, with their template
	 */
	record EventFiles(Map<Integer, List<FileRow>> errandFiles, Map<Integer, List<FileRow>> unplacedFiles, List<DocumentRow> documents) {

		static EventFiles of(final List<EventFile> events, final List<FileRow> pictures, final Set<Integer> errandIds) {
			final Predicate<EventFile> withErrand = event -> event.arendeId() != null && errandIds.contains(event.arendeId());
			final var errandFiles = Stream.concat(
				events.stream().filter(withErrand).flatMap(event -> event.files(event.arendeId())),
				pictures.stream().filter(row -> row.name() != null && row.arendeId() != null))
				.collect(groupingBy(FileRow::arendeId));
			final var unplacedFiles = events.stream()
				.filter(withErrand.negate().and(event -> event.objektId() == null))
				.collect(groupingBy(EventFile::handelseId, LinkedHashMap::new, flatMapping(event -> event.files(null), toList())));
			final var documents = events.stream()
				.filter(event -> event.worddokument() != null)
				.map(event -> new DocumentRow(event.handelseId(), event.arendeId(), event.objektId(), event.mall(), event.tidpunkt(), event.worddokument()))
				.toList();
			return new EventFiles(errandFiles, unplacedFiles, documents);
		}
	}
}
