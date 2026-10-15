package se.sundsvall.aotloader.source.alkol;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.InspectionVisitRows;
import se.sundsvall.aotloader.source.InspectionVisitRows.RemarkRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitFileRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.source.PartyRows;
import se.sundsvall.aotloader.source.PersonRow;
import se.sundsvall.aotloader.source.SourceDatabases;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.EventRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.PermitViewRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.SalesPlaceRow;

import static java.util.stream.Collectors.groupingBy;
import static se.sundsvall.aotloader.service.model.Source.ALKOL;
import static se.sundsvall.aotloader.source.Rows.byKey;
import static se.sundsvall.aotloader.source.Rows.date;
import static se.sundsvall.aotloader.source.Rows.dateTime;
import static se.sundsvall.aotloader.source.Rows.flag;
import static se.sundsvall.aotloader.source.Rows.integer;
import static se.sundsvall.aotloader.source.Rows.isSet;
import static se.sundsvall.aotloader.source.Rows.text;

/**
 * Reads AlkOL, the database of the sales of tobacco, folköl, e-cigarettes and tobacco-free nicotine products. The
 * queries only select. The view FS_GT_Tobak is read as the system's own list of current tobacco permits (sections 7.4
 * and 7.7); its columns are read whatever their type, since its definition is not visible to the account.
 */
@Repository
public class AlkolRepository {

	private static final String SELECT_PLACES = """
		SELECT f.FSID, f.Namn, f.GatuAdress, f.PostNummer, f.PostOrt, f.Telefon1, f.Email, f.[Område], f.Kategori,
		       f.[ÄgareOrgNr], f.Notering, f.OLTYP, f.TOBAKTYP, f.ECIGTYP, f.TobaksfriNikotinTyp, f.Upphort, f.Agarid
		FROM dbo.[FörsäljningsStällen] f
		""";

	private static final String SELECT_PERMIT_VIEW = """
		SELECT v.FSID, v.GTDE, v.GTPH, v.GTDI
		FROM dbo.FS_GT_Tobak v
		""";

	private static final String SELECT_OWNERS = """
		SELECT a.AgarID, a.OrganisationsNr, a.Bolagsnamn, a.KontaktPerson, a.GAdress, a.GAdress2, a.PNr, a.POrt,
		       a.TelefonNr1, a.EMail
		FROM dbo.[Ägare] a
		""";

	private static final String SELECT_ERRANDS = """
		SELECT ar.ArendeID, ar.ObjektID, ar.Arendetyp, ar.DiarieNr, ar.OppnandeDatum, ar.AvslutsDatum, ar.Sokandeid
		FROM dbo.[Ärende] ar
		""";

	private static final String SELECT_DECISIONS = """
		SELECT b.BeslutsID, b.ArendeID, b.BeslutsTyp, b.BeslutsDatumTid, b.GiltigtFROM_Datum,
		       b.Dokument, b.Worddokument, b.Bifogadfil
		FROM dbo.Beslut b
		""";

	private static final String SELECT_EVENTS = """
		SELECT h.HandelseID, h.FSID, h.ArendeID, h.HandelseTyp, h.HandelseDatumTid, h.DiarieNr, h.Dokument, h.Worddokument, h.Bifogadfil
		FROM dbo.[Händelser] h
		""";

	private static final String SELECT_APPLICANTS = """
		SELECT s.SokandeID, s.OrganisationsNr, s.Bolagsnamn, s.KontaktPerson, s.GAdress, s.GAdress2, s.PNr, s.POrt,
		       s.TelefonNr1, s.EMail
		FROM dbo.[Sökande] s
		""";

	private static final String SELECT_PERSONS = """
		SELECT p.AgarID, p.Namn, p.Personnr, p.Befattningskod1, p.Befattningskod2, p.Befattningskod3, p.UtlandskPerson
		FROM dbo.[PBI_ägare] p
		WHERE p.AgarID IS NOT NULL
		  AND (p.PASSIV IS NULL OR p.PASSIV = 0)
		""";

	private static final String SELECT_VISITS = """
		SELECT t.TillsynsTillfalleNr, t.ObjektId, t.StartDatum, t.StartTid, t.SlutTid, t.Tillsynsform, t.Fritext,
		       t.WordDokument, t.Bifogadfil
		FROM dbo.[Tillsyn_Tillfälle] t
		""";

	private static final String SELECT_VISIT_REMARKS = """
		SELECT a.TillsynsTillfalleNr, a.Anmarkningskod
		FROM dbo.[Tillsyn_Anmärkningar] a
		ORDER BY a.TillsynsTillfalleNr, a.Anmarkningskod
		""";

	private static final String SELECT_VISIT_FILES = """
		SELECT f.TillsynsTillfalleNr, f.Bifogadfil
		FROM dbo.[Tillsyn_Tillfälle_Filer] f
		WHERE f.TillsynsTillfalleNr IS NOT NULL
		  AND f.Bifogadfil IS NOT NULL
		ORDER BY f.ID
		""";

	// Kod is no key: an active row of a code gives its text before a passive one
	private static final String SELECT_CODE_TABLE = "SELECT c.Kod, c.Klartext FROM dbo.%s c ORDER BY c.Kod, ISNULL(c.Passiv, 0), c.Klartext";
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final String ARENDE_ID = "ArendeID";
	private static final String TILLSYNSTILLFALLE_NR = "TillsynsTillfalleNr";
	private static final String BIFOGADFIL = "Bifogadfil";

	private final SourceDatabases sourceDatabases;

	AlkolRepository(final SourceDatabases sourceDatabases) {
		this.sourceDatabases = sourceDatabases;
	}

	/**
	 * @return what the migration needs from AlkOL
	 */
	public AlkolSnapshot readSnapshot() {
		final var jdbc = sourceDatabases.jdbcTemplate(ALKOL);
		return new AlkolSnapshot(
			// FSID, AgarID, ArendeID and SokandeID are primary keys, and the query of the persons leaves out rows without owner
			jdbc.query(SELECT_PLACES, (rs, _) -> toPlace(rs)),
			byKey(jdbc.query(SELECT_PERMIT_VIEW, (rs, _) -> toPermitView(rs)), PermitViewRow::fsId),
			byKey(jdbc.query(SELECT_OWNERS, (rs, _) -> PartyRows.toOwner(rs)), ContactRow::id),
			jdbc.query(SELECT_ERRANDS, (rs, _) -> toErrand(rs)),
			jdbc.query(SELECT_DECISIONS, (rs, _) -> toDecision(rs)),
			jdbc.query(SELECT_EVENTS, (rs, _) -> toEvent(rs)),
			byKey(jdbc.query(SELECT_APPLICANTS, (rs, _) -> PartyRows.toApplicant(rs)), ContactRow::id),
			jdbc.query(SELECT_PERSONS, (rs, _) -> PartyRows.toPerson(rs)).stream().filter(row -> row.agarId() != null).collect(groupingBy(PersonRow::agarId)),
			codeTable(jdbc, "Beslutskoder"),
			codeTable(jdbc, "[Ärendetyper]"),
			codeTable(jdbc, "Kategorier"),
			codeTable(jdbc, "[Områden]"),
			codeTable(jdbc, "PBI_Roller"),
			new InspectionVisitRows(
				jdbc.query(SELECT_VISITS, (rs, _) -> toVisit(rs)).stream().filter(row -> row.tillfalleNr() != null).toList(),
				jdbc.query(SELECT_VISIT_REMARKS, (rs, _) -> toRemark(rs)).stream().filter(row -> row.tillfalleNr() != null).collect(groupingBy(RemarkRow::tillfalleNr)),
				InspectionVisitRows.filesByVisit(jdbc.query(SELECT_VISIT_FILES, (rs, _) -> new VisitFileRow(integer(rs, TILLSYNSTILLFALLE_NR), text(rs, BIFOGADFIL)))),
				codeTable(jdbc, "TillsynsArt"),
				codeTable(jdbc, "Anmarkningar")));
	}

	private static Map<String, String> codeTable(final JdbcTemplate jdbc, final String table) {
		final var texts = new HashMap<String, String>();
		jdbc.query(SELECT_CODE_TABLE.formatted(table), rs -> {
			final var code = text(rs, "Kod");
			final var codeText = text(rs, "Klartext");
			if (code != null && codeText != null) {
				texts.putIfAbsent(code, codeText);
			}
		});
		return texts;
	}

	static SalesPlaceRow toPlace(final ResultSet rs) throws SQLException {
		return new SalesPlaceRow(
			integer(rs, "FSID"),
			text(rs, "Namn"),
			text(rs, "GatuAdress"),
			text(rs, "PostNummer"),
			text(rs, "PostOrt"),
			text(rs, "Telefon1"),
			text(rs, "Email"),
			text(rs, "Område"),
			text(rs, "Kategori"),
			text(rs, "ÄgareOrgNr"),
			text(rs, "Notering"),
			flag(rs, "OLTYP"),
			flag(rs, "TOBAKTYP"),
			flag(rs, "ECIGTYP"),
			flag(rs, "TobaksfriNikotinTyp"),
			flag(rs, "Upphort"),
			integer(rs, "Agarid"));
	}

	static PermitViewRow toPermitView(final ResultSet rs) throws SQLException {
		return new PermitViewRow(integer(rs, "FSID"), isSet(rs, "GTDE"), isSet(rs, "GTPH"), isSet(rs, "GTDI"));
	}

	static ErrandRow toErrand(final ResultSet rs) throws SQLException {
		return new ErrandRow(
			integer(rs, ARENDE_ID),
			integer(rs, "ObjektID"),
			text(rs, "Arendetyp"),
			text(rs, "DiarieNr"),
			date(rs, "OppnandeDatum"),
			date(rs, "AvslutsDatum"),
			integer(rs, "Sokandeid"));
	}

	static DecisionRow toDecision(final ResultSet rs) throws SQLException {
		return new DecisionRow(
			integer(rs, "BeslutsID"),
			integer(rs, ARENDE_ID),
			text(rs, "BeslutsTyp"),
			dateTime(rs, "BeslutsDatumTid"),
			date(rs, "GiltigtFROM_Datum"),
			text(rs, "Dokument"),
			text(rs, "Worddokument"),
			text(rs, BIFOGADFIL));
	}

	static VisitRow toVisit(final ResultSet rs) throws SQLException {
		return new VisitRow(
			integer(rs, TILLSYNSTILLFALLE_NR),
			integer(rs, "ObjektId"),
			date(rs, "StartDatum"),
			time(rs, "StartTid"),
			time(rs, "SlutTid"),
			text(rs, "Tillsynsform"),
			text(rs, "Fritext"),
			text(rs, "WordDokument"),
			text(rs, BIFOGADFIL));
	}

	static RemarkRow toRemark(final ResultSet rs) throws SQLException {
		return new RemarkRow(integer(rs, TILLSYNSTILLFALLE_NR), text(rs, "Anmarkningskod"), null, null);
	}

	/**
	 * AlkOL keeps the times of a visit as datetime on 1899-12-30, of which only the time of day counts. 00:00 counts as no
	 * time, since that is what the field holds when no time was entered: almost half of the visits have it.
	 */
	static String time(final ResultSet rs, final String column) throws SQLException {
		final var value = dateTime(rs, column);
		return value == null || LocalTime.MIDNIGHT.equals(value.toLocalTime()) ? null : value.format(TIME);
	}

	static EventRow toEvent(final ResultSet rs) throws SQLException {
		return new EventRow(
			integer(rs, "HandelseID"),
			integer(rs, "FSID"),
			integer(rs, ARENDE_ID),
			text(rs, "HandelseTyp"),
			dateTime(rs, "HandelseDatumTid"),
			text(rs, "DiarieNr"),
			text(rs, "Dokument"),
			text(rs, "Worddokument"),
			text(rs, BIFOGADFIL));
	}
}
