package se.sundsvall.aotloader.source;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Database;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Inspection;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Roles;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReaderResult.Notice;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.MigrationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;

class SourceSupportTest {

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
		assertThat(new FileList().add(" a.pdf ", ATTACHMENTS).add("A.PDF", ATTACHMENTS).add("a.pdf", DOCUMENTS).add(" ", DOCUMENTS).add(null, DOCUMENTS).build())
			.containsExactly(new FileReference("a.pdf", ATTACHMENTS), new FileReference("a.pdf", DOCUMENTS));
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
		final var properties = new AotLoaderProperties("ALKT", "s", "u", "c", null, new Roles("PRIMARY", "CONTACT", null), Map.of(), new Inspection(true, Map.of(), Map.of()),
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
		assertThat(properties.inspection().errandTypes(Source.ALKOL)).containsOnlyKeys("it");

		final var empty = new AotLoaderProperties("ALKT", "s", "u", "c", null, new Roles("PRIMARY", "CONTACT", null), null, new Inspection(false, Map.of(" Tills ", "x"), null), null, null,
			DataSize.ofMegabytes(1), null);
		assertThat(empty.errandType(ErrandType.SERVING_PERMIT).category()).isNull();
		assertThat(empty.database(Source.ALKT)).isEmpty();
		assertThat(empty.fileArea(Source.ALKT).documents()).isNull();
		assertThat(empty.inspection().errandTypes(Source.ALKT)).containsEntry("tills", "x");
		assertThat(empty.inspection().errandTypes(Source.ALKOL)).isEmpty();
		assertThat(new Database("x", null, null, null).queryTimeout()).isEqualTo(Duration.ofMinutes(2));
	}

	private static Candidate candidate(final Kind kind, final String sourceId) {
		return Candidate.builder(new ItemKey(Source.ALKT, kind, sourceId), ErrandType.SERVING_PERMIT).build();
	}
}
