package se.sundsvall.aotloader.source;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.util.unit.DataSize;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Database;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.ErrandTypeSettings;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.FileAreaDirectories;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Roles;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.service.model.AssetFile;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReaderResult.Notice;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.files.SharedFileNames;
import se.sundsvall.aotloader.support.MigrationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;

class SourceSupportTest {

	private static final FileAreaDirectories TWO_DIRECTORIES = new FileAreaDirectories("//server/alkt/Dokument", "//server/alkt/Bilder");

	@Test
	void readerResultsAreOrderedByKindAndId() {
		final var notice = new Notice(new ItemKey(Source.ALKOL, Kind.ANMALAN, "1-INGEN"), null, Outcome.MIGRERAS_INTE, null);
		final var result = ReaderResults.combine(List.of(
			new ReaderResult(List.of(candidate(Kind.TILLSYN, "5"), candidate(Kind.TILLSTAND, "10")), List.of(notice)),
			new ReaderResult(List.of(candidate(Kind.TILLSTAND, "9"), candidate(Kind.ANMALAN, "2-FOLKOL"), candidate(Kind.ANMALAN, "10-ECIGARETTER"), candidate(Kind.ANMALAN, "2-ECIGARETTER")), List.of())));

		assertThat(result.candidates()).extracting(candidate -> candidate.key().kind() + "/" + candidate.key().sourceId()).containsExactly(
			"TILLSTAND/9", "TILLSTAND/10", "ANMALAN/2-ECIGARETTER", "ANMALAN/2-FOLKOL", "ANMALAN/10-ECIGARETTER", "TILLSYN/5");
		assertThat(result.notices()).containsExactly(notice);
	}

	@Test
	void parameterAndFileLists() {
		assertThat(new ParameterList().add("a", "A", (String) null).add("b", "B", " x ").add("c", "C", java.util.Arrays.asList(" ", null, "y")).build())
			.extracting(parameter -> parameter.key() + "=" + parameter.values()).containsExactly("b=[x]", "c=[y]");
		assertThat(new FileList(SharedFileNames.builder(TWO_DIRECTORIES).build()).add(" a.pdf ", ATTACHMENTS).add("A.PDF", ATTACHMENTS).add("a.pdf", DOCUMENTS).add(" ", DOCUMENTS).add(null, DOCUMENTS).build())
			.containsExactly(new FileReference("a.pdf", ATTACHMENTS), new FileReference("a.pdf", DOCUMENTS));
	}

	@Test
	void aFileForThePostKeepsTheCategoryItIsFirstMarkedWithAndIsAFileOfTheErrandToo() {
		final var shared = SharedFileNames.builder(TWO_DIRECTORIES).add("objekt:1", "delad.pdf", ATTACHMENTS).add("objekt:2", "delad.pdf", ATTACHMENTS).build();
		final var files = new FileList(shared)
			.add("ansokan.pdf", ATTACHMENTS)
			.add("bevis.rtf", DOCUMENTS, "Tillståndsbevis")
			.add("BEVIS.RTF", DOCUMENTS, "Beslut")
			.add("delad.pdf", ATTACHMENTS, "Ritning")
			.add(" ", ATTACHMENTS, "Ritning")
			.add("ansokan.pdf", ATTACHMENTS, "Beslut");

		assertThat(files.build()).containsExactly(
			new FileReference("ansokan.pdf", ATTACHMENTS),
			new FileReference("bevis.rtf", DOCUMENTS),
			new FileReference("delad.pdf", ATTACHMENTS, true));
		assertThat(files.assetFiles()).containsExactly(
			new AssetFile(new FileReference("bevis.rtf", DOCUMENTS), "Tillståndsbevis"),
			new AssetFile(new FileReference("delad.pdf", ATTACHMENTS, true), "Ritning"),
			new AssetFile(new FileReference("ansokan.pdf", ATTACHMENTS), "Beslut"));

		final var errandOnly = new FileList(shared)
			.addToErrandOnly("gammalt-bevis.rtf", DOCUMENTS)
			.add("GAMMALT-BEVIS.rtf", DOCUMENTS, "Beslut")
			.add("bevis.rtf", DOCUMENTS, "Tillståndsbevis")
			.addToErrandOnly("bevis.rtf", DOCUMENTS);
		assertThat(errandOnly.build()).extracting(FileReference::name).containsExactly("gammalt-bevis.rtf", "bevis.rtf");
		assertThat(errandOnly.assetFiles()).containsExactly(new AssetFile(new FileReference("bevis.rtf", DOCUMENTS), "Tillståndsbevis"));

		final var withoutCategory = new FileList(shared)
			.add("anmalan.pdf", ATTACHMENTS, null)
			.add("ANMALAN.pdf", ATTACHMENTS, "Ritning");
		assertThat(withoutCategory.assetFiles()).as("being marked without a category is a first mark too")
			.containsExactly(new AssetFile(new FileReference("anmalan.pdf", ATTACHMENTS), null));
	}

	@Test
	void permitFiles() {
		assertThat(PermitFiles.isDrawing("Planritning.PDF")).isTrue();
		assertThat(PermitFiles.isDrawing("RITNING våning 2.pdf")).isTrue();
		assertThat(PermitFiles.isDrawing("ritn.pdf")).isFalse();
		assertThat(PermitFiles.isDrawing(null)).isFalse();
		assertThat(PermitFiles.isTemplate(" tdbv2 ", Set.of("TDBV2"))).isTrue();
		assertThat(PermitFiles.isTemplate("TDBV", Set.of("TDBV2"))).isFalse();
		assertThat(PermitFiles.isTemplate(null, Set.of("TDBV2"))).isFalse();
		assertThat(PermitFiles.isCertificate("Tillståndsbevis 2023.pdf")).isTrue();
		assertThat(PermitFiles.isCertificate("TILLSTANDS_BEVIS.pdf")).isTrue();
		assertThat(PermitFiles.isCertificate("Mottagningsbevis.pdf")).isFalse();
		assertThat(PermitFiles.isCertificate("Delgivningsbevis tillstånd.pdf")).isFalse();
		assertThat(PermitFiles.isCertificate(null)).isFalse();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
		Beslut 2019.pdf                  | true
		BESLUTSBILAGA.pdf                | true
		Tillstånd serveringen.pdf        | true
		tillstandsbevis.pdf              | true
		Beslutsunderlag.pdf              | false
		Tillståndsutredning.pdf          | false
		Remiss tillstånd.pdf             | false
		Yttrande beslut.pdf              | false
		Kommunicering beslut.pdf         | false
		Ansökan om tillstånd.pdf         | false
		ansokan tillstand.pdf            | false
		Begäran om komplettering beslut  | false
		Mottagningsbevis.pdf             | false
		Registreringsbevis.pdf           | false
		Registerutdrag tillstånd.pdf     | false
		bilaga.pdf                       | false
		""")
	void aDecisionIsKnownByItsName(final String name, final boolean decision) {
		assertThat(PermitFiles.isDecision(name)).isEqualTo(decision);
	}

	@Test
	void aFileWithoutANameIsNoDecision() {
		assertThat(PermitFiles.isDecision(null)).isFalse();
	}

	@Test
	void aFileNameThatAnotherPlaceAlsoRefersToIsShared() {
		final var shared = SharedFileNames.builder(TWO_DIRECTORIES)
			.add("objekt:1", "Ansökan.pdf", ATTACHMENTS)
			.add("objekt:2", "C:\\Bilder\\ansökan.pdf ", ATTACHMENTS)
			.add("objekt:1", "ritning.pdf", ATTACHMENTS)
			.add("objekt:1", "Ritning.pdf", ATTACHMENTS)
			.add("objekt:1", "11111111.rtf", DOCUMENTS)
			.add("objekt:2", "11111111.rtf", ATTACHMENTS)
			.add("objekt:2", " ", ATTACHMENTS)
			.add("objekt:3", null, ATTACHMENTS)
			.build();

		assertThat(shared.isShared("ANSÖKAN.PDF", ATTACHMENTS)).as("another place, other case and a path").isTrue();
		assertThat(shared.isShared("ritning.pdf", ATTACHMENTS)).as("the same place twice").isFalse();
		assertThat(shared.isShared("11111111.rtf", DOCUMENTS)).as("another directory").isFalse();
		assertThat(shared.isShared("okänd.pdf", ATTACHMENTS)).isFalse();
		assertThat(shared.isShared(" ", ATTACHMENTS)).isFalse();
		assertThat(shared.isShared(null, ATTACHMENTS)).isFalse();
		assertThat(new FileList(shared).add("ansökan.pdf", ATTACHMENTS).add("ritning.pdf", ATTACHMENTS).build())
			.containsExactly(new FileReference("ansökan.pdf", ATTACHMENTS, true), new FileReference("ritning.pdf", ATTACHMENTS, false));
		final var builder = SharedFileNames.builder(TWO_DIRECTORIES);
		assertThatThrownBy(() -> builder.add(null, "a.pdf", ATTACHMENTS)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void aSourceWithOneDirectoryForDocumentsAndAttachmentsSharesANameAcrossThem() {
		final var oneDirectory = SharedFileNames.builder(new FileAreaDirectories("//server/alkol/Dok", "//server/alkol/Dok/"))
			.add("fs:1", "11111111.rtf", DOCUMENTS)
			.add("fs:2", "11111111.RTF", ATTACHMENTS)
			.build();
		final var notConfigured = SharedFileNames.builder(new FileAreaDirectories(null, null))
			.add("fs:1", "11111111.rtf", DOCUMENTS)
			.add("fs:2", "11111111.rtf", ATTACHMENTS)
			.build();

		assertThat(oneDirectory.isShared("11111111.rtf", DOCUMENTS)).isTrue();
		assertThat(oneDirectory.isShared("11111111.rtf", ATTACHMENTS)).isTrue();
		assertThat(notConfigured.isShared("11111111.rtf", DOCUMENTS)).isFalse();
		assertThat(SharedFileNames.builder(null).add("fs:1", "a.pdf", DOCUMENTS).add("fs:2", "a.pdf", ATTACHMENTS).build().isShared("a.pdf", DOCUMENTS)).isFalse();
	}

	@Test
	void rows() throws SQLException {
		final var rs = mock(ResultSet.class);
		when(rs.getString("t")).thenReturn("  text  ");
		when(rs.getString("blank")).thenReturn("  ");
		when(rs.getTimestamp("d")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 1, 2, 23, 59)));
		when(rs.getBoolean("f")).thenReturn(true);
		when(rs.getInt("i")).thenReturn(5);
		when(rs.getInt("null")).thenReturn(0);
		when(rs.wasNull()).thenReturn(false, true);
		when(rs.getObject("bit")).thenReturn(Boolean.TRUE);
		when(rs.getObject("number")).thenReturn(0);
		when(rs.getObject("text")).thenReturn(" FALSE ");
		when(rs.getObject("date")).thenReturn(Timestamp.valueOf(LocalDateTime.of(2026, 1, 2, 0, 0)));
		when(rs.getObject("other")).thenReturn("GTDE");

		assertThat(Rows.text(rs, "t")).isEqualTo("text");
		assertThat(Rows.text(rs, "blank")).isNull();
		assertThat(Rows.date(rs, "d")).isEqualTo(LocalDate.of(2026, 1, 2));
		assertThat(Rows.date(rs, "none")).isNull();
		assertThat(Rows.dateTime(rs, "d")).isEqualTo(LocalDateTime.of(2026, 1, 2, 23, 59));
		assertThat(Rows.dateTime(rs, "none")).isNull();
		assertThat(Rows.flag(rs, "f")).isTrue();
		assertThat(Rows.integer(rs, "i")).isEqualTo(5);
		assertThat(Rows.integer(rs, "null")).isNull();
		assertThat(Rows.isSet(rs, "bit")).isTrue();
		assertThat(Rows.isSet(rs, "number")).isFalse();
		assertThat(Rows.isSet(rs, "text")).isFalse();
		assertThat(Rows.isSet(rs, "date")).isTrue();
		assertThat(Rows.isSet(rs, "other")).isTrue();
		assertThat(Rows.isSet(rs, "missing")).isFalse();
	}

	@Test
	void sourceDatabasesOpenOnFirstUseAndOnlyWhenConfigured() {
		final var properties = new AotLoaderProperties("AOT", "s", "u", "c", null, new Roles("PRIMARY", "CONTACT", null), Map.of(),
			Map.of(Source.ALKT, new Database("jdbc:h2:mem:source;DB_CLOSE_DELAY=-1", "sa", "", Duration.ofSeconds(5)), Source.ALKOL, new Database(" ", null, null, null)), Map.of(), DataSize.ofMegabytes(1), null);
		final var databases = new SourceDatabases(properties);

		final var jdbc = databases.jdbcTemplate(Source.ALKT);
		assertThat(jdbc.getQueryTimeout()).isEqualTo(5);
		assertThat(jdbc.queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
		assertThat(databases.jdbcTemplate(Source.ALKT).getDataSource()).isSameAs(jdbc.getDataSource());
		assertThatThrownBy(() -> databases.jdbcTemplate(Source.ALKOL))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Källdatabasen för AlkOL är inte konfigurerad");

		databases.destroy();
	}

	@Test
	void properties() {
		final var properties = TestData.properties();

		assertThat(properties.errandType(ErrandType.SERVING_PERMIT).assetType()).isEqualTo("AssetSERVING_PERMIT");
		assertThat(properties.database(Source.ALKT)).isPresent();
		assertThat(properties.fileArea(Source.ALKT).documents()).isEqualTo("documents");
		assertThat(properties.fileArea(Source.ALKT)).hasToString("FileAreaDirectories[configured=true]");
		assertThat(properties.database(Source.ALKT).orElseThrow()).hasToString("Database[configured=true]");
		assertThat(properties.errandType(ErrandType.TOBACCO_INSPECTION).sourceErrandTypes()).containsOnlyKeys("it");

		final var empty = new AotLoaderProperties("AOT", "s", "u", "c", null, new Roles("PRIMARY", "CONTACT", null), null, null, null, DataSize.ofMegabytes(1), null);
		final var none = empty.errandType(ErrandType.SERVING_PERMIT);
		assertThat(none.category()).isNull();
		assertThat(none.labels()).isEmpty();
		assertThat(none.subtypeLabels()).isEmpty();
		assertThat(none.sourceErrandTypes()).isEmpty();
		assertThat(none.onlyPlacesWithPermit()).isFalse();
		assertThat(none.allLabels()).isEmpty();
		assertThat(empty.database(Source.ALKT)).isEmpty();
		assertThat(empty.fileArea(Source.ALKT).documents()).isNull();
		assertThat(new Database("x", null, null, null).queryTimeout()).isEqualTo(Duration.ofMinutes(2));
	}

	@Test
	void errandTypeSettingsAreTrimmed() {
		final var subtypeLabels = new EnumMap<ServingPermitSubtype, String>(ServingPermitSubtype.class);
		subtypeLabels.put(ServingPermitSubtype.PERMANENT_SERVING, " B ");
		subtypeLabels.put(ServingPermitSubtype.TASTING, null);
		final var settings = new ErrandTypeSettings(null, null, null, null, Arrays.asList(" A ", null, " "), subtypeLabels, Map.of(" Tills ", " C ", "IT", " "), true);

		assertThat(settings.labels()).containsExactly("A");
		assertThat(settings.subtypeLabel(ServingPermitSubtype.PERMANENT_SERVING)).contains("B");
		assertThat(settings.subtypeLabel(ServingPermitSubtype.TASTING)).isEmpty();
		assertThat(settings.subtypeLabel(ServingPermitSubtype.FARM_SALES)).isEmpty();
		assertThat(settings.sourceErrandTypes()).containsOnly(entry("tills", "C"), entry("it", ""));
		assertThat(settings.allLabels()).containsExactlyInAnyOrder("A", "B", "C");
		// The errand types of the source are looked up whatever their case and blanks
		assertThat(settings.sourceErrandType(" TILLS")).contains("C");
		assertThat(settings.sourceErrandType("it")).contains("");
		assertThat(settings.sourceErrandType("07")).isEmpty();
		assertThat(settings.sourceErrandType(null)).isEmpty();
	}

	@Test
	void issuedLine() {
		assertThat(CandidateTexts.issuedLine(LocalDate.of(2019, 5, 1), "IAN-2019-1")).isEqualTo("Utfärdat: 2019-05-01 (IAN-2019-1)");
		assertThat(CandidateTexts.issuedLine(LocalDate.of(2019, 5, 1), null)).isEqualTo("Utfärdat: 2019-05-01");
		assertThat(CandidateTexts.issuedLine(null, "IAN-2019-1")).isEqualTo("Utfärdat: (IAN-2019-1)");
		assertThat(CandidateTexts.issuedLine(null, null)).isNull();
	}

	private static Candidate candidate(final Kind kind, final String sourceId) {
		return Candidate.builder(new ItemKey(Source.ALKT, kind, sourceId), ErrandType.SERVING_PERMIT).build();
	}
}
