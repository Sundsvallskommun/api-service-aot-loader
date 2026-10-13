package se.sundsvall.aotloader.source.alkt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.PartyRows;
import se.sundsvall.aotloader.source.PersonRow;
import se.sundsvall.aotloader.source.SourceDatabases;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ConditionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
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
		SELECT b.BeslutsID, b.ArendeID, b.BeslutsTyp, b.BeslutsDatumTid, b.GiltigtTOM_Datum, b.Worddokument, b.BifogadFil
		FROM dbo.[Ärende_Beslut] b
		""";

	private static final String SELECT_ERRANDS = """
		SELECT ar.ArendeID, ar.ObjektID, ar.ArendeTyp, ar.DiarieNr, ar.OppnandeDatum, ar.AvslutsDatum, ar.SokandeID
		FROM dbo.[Ärende] ar
		""";

	private static final String SELECT_EVENT_FILES = """
		SELECT h.ArendeID, h.Worddokument, h.Bifogadfil
		FROM dbo.[Ärende_Händelser] h
		WHERE h.ArendeID IS NOT NULL
		  AND (h.Worddokument IS NOT NULL OR h.Bifogadfil IS NOT NULL)
		""";

	private static final String SELECT_EVENT_EXTRA_FILES = """
		SELECT h.ArendeID, f.Bifogadfil
		FROM dbo.[Ärende_Händelser_Filer] f
		JOIN dbo.[Ärende_Händelser] h ON h.HandelseID = f.HandelseID
		WHERE h.ArendeID IS NOT NULL
		  AND f.Bifogadfil IS NOT NULL
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

	private final SourceDatabases sourceDatabases;

	AlktRepository(final SourceDatabases sourceDatabases) {
		this.sourceDatabases = sourceDatabases;
	}

	/**
	 * @return what the migration needs from AlkT
	 */
	public AlktSnapshot readSnapshot() {
		final var jdbc = sourceDatabases.jdbcTemplate(ALKT);
		final var fileRows = Stream.of(
			jdbc.query(SELECT_EVENT_FILES, AlktRepository::toEventFiles).stream().flatMap(List::stream),
			jdbc.query(SELECT_EVENT_EXTRA_FILES, (rs, i) -> new FileRow(integer(rs, "ArendeID"), text(rs, "Bifogadfil"), false)).stream(),
			jdbc.query(SELECT_PICTURES, (rs, i) -> new FileRow(integer(rs, "ArendeID"), text(rs, "Bildfilnamn"), false)).stream())
			.flatMap(s -> s)
			.filter(row -> row.name() != null && row.arendeId() != null)
			.collect(groupingBy(FileRow::arendeId));

		return new AlktSnapshot(
			jdbc.query(SELECT_PERMITS, AlktRepository::toPermit),
			byKey(jdbc.query(SELECT_PLACES, AlktRepository::toPlace), PlaceRow::objektId),
			byKey(jdbc.query(SELECT_OWNERS, PartyRows::toOwner), ContactRow::id),
			jdbc.query(SELECT_CONDITIONS, AlktRepository::toCondition).stream().filter(row -> row.objektId() != null).collect(groupingBy(ConditionRow::objektId)),
			byKey(jdbc.query(SELECT_DECISIONS, AlktRepository::toDecision), DecisionRow::beslutsId),
			byKey(jdbc.query(SELECT_ERRANDS, AlktRepository::toErrand), ErrandRow::arendeId),
			fileRows,
			jdbc.query(SELECT_PERSONS, PartyRows::toPerson).stream().filter(row -> row.agarId() != null).collect(groupingBy(PersonRow::agarId)),
			byKey(jdbc.query(SELECT_APPLICANTS, PartyRows::toApplicant), ContactRow::id),
			readCodeTexts(jdbc));
	}

	private static Map<String, Map<String, String>> readCodeTexts(final JdbcTemplate jdbc) {
		final var texts = new HashMap<String, Map<String, String>>();
		jdbc.query(SELECT_CODE_TEXTS, rs -> {
			final var codeType = text(rs, "Kodtyp");
			final var code = text(rs, "Kod");
			final var codeText = text(rs, "Klartext");
			if (codeType != null && code != null && codeText != null) {
				texts.computeIfAbsent(codeType, key -> new HashMap<>()).put(code, codeText);
			}
		});
		return texts;
	}

	static PermitRow toPermit(final ResultSet rs, final int rowNum) throws SQLException {
		final var servingTimes = new ArrayList<ServingTime>();
		for (var i = 1; i <= SERVING_TIME_INTERVALS; i++) {
			servingTimes.add(new ServingTime(text(rs, "ServeringStartTid" + i), text(rs, "ServeringSlutTid" + i)));
		}
		final var notes = new ArrayList<String>();
		for (final var column : List.of("Noteringar", "Noteringar2", "Noteringar3", "Noteringar4", "Noteringar5", "Noteringar5B", "Noteringar6", "Noteringar7", "Noteringar8")) {
			notes.add(text(rs, column));
		}
		return new PermitRow(
			integer(rs, "ObjektID"),
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

	static PlaceRow toPlace(final ResultSet rs, final int rowNum) throws SQLException {
		return new PlaceRow(
			integer(rs, "ObjektID"),
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

	static ConditionRow toCondition(final ResultSet rs, final int rowNum) throws SQLException {
		return new ConditionRow(integer(rs, "ObjID"), text(rs, "Villkor"), text(rs, "Notering"), text(rs, "FR_OM"), text(rs, "T_OM"));
	}

	static DecisionRow toDecision(final ResultSet rs, final int rowNum) throws SQLException {
		return new DecisionRow(
			integer(rs, "BeslutsID"),
			integer(rs, "ArendeID"),
			text(rs, "BeslutsTyp"),
			date(rs, "BeslutsDatumTid"),
			date(rs, "GiltigtTOM_Datum"),
			text(rs, "Worddokument"),
			text(rs, "BifogadFil"));
	}

	static ErrandRow toErrand(final ResultSet rs, final int rowNum) throws SQLException {
		return new ErrandRow(
			integer(rs, "ArendeID"),
			integer(rs, "ObjektID"),
			text(rs, "ArendeTyp"),
			text(rs, "DiarieNr"),
			date(rs, "OppnandeDatum"),
			date(rs, "AvslutsDatum"),
			integer(rs, "SokandeID"));
	}

	static List<FileRow> toEventFiles(final ResultSet rs, final int rowNum) throws SQLException {
		final var arendeId = integer(rs, "ArendeID");
		return List.of(
			new FileRow(arendeId, text(rs, "Worddokument"), true),
			new FileRow(arendeId, text(rs, "Bifogadfil"), false));
	}
}
