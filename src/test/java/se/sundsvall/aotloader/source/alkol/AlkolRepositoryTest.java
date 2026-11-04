package se.sundsvall.aotloader.source.alkol;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlkolRepositoryTest {

	@Test
	void theTimeOfAVisitIsTheTimeOfDayAndMidnightIsNoTime() throws SQLException {
		final var rs = mock(ResultSet.class);
		when(rs.getObject("StartTid", LocalDateTime.class)).thenReturn(LocalDateTime.of(1899, 12, 30, 14, 5));
		when(rs.getObject("SlutTid", LocalDateTime.class)).thenReturn(LocalDateTime.of(1899, 12, 30, 0, 0));

		assertThat(AlkolRepository.time(rs, "StartTid")).isEqualTo("14:05");
		assertThat(AlkolRepository.time(rs, "SlutTid")).isNull();
		assertThat(AlkolRepository.time(rs, "Saknas")).isNull();
	}

	@Test
	void whatTheInspectionCoveredInTheOrderTheVisitShowsIt() throws SQLException {
		final var rs = mock(ResultSet.class);
		when(rs.getBoolean("TTypNIKOTIN")).thenReturn(true);
		when(rs.getBoolean("TTypOL")).thenReturn(true);
		when(rs.getBoolean("TTypTOBAK")).thenReturn(true);

		assertThat(AlkolRepository.covered(rs)).containsExactly("Tobak", "Folköl", "Nikotin");
		assertThat(AlkolRepository.covered(mock(ResultSet.class))).isEmpty();
	}

	@Test
	void theUsersAreNamedByTheirNumberAndByTheirCode() throws SQLException {
		final var jdbc = mock(JdbcTemplate.class);
		final var rs = mock(ResultSet.class);
		// A user with a name, one with a blank name, one without a number and a later user with the code of the first
		when(rs.getInt("AnvID")).thenReturn(7, 8, 0, 9);
		when(rs.wasNull()).thenReturn(false, false, true, false);
		when(rs.getString("AnvändarKod")).thenReturn("PE", "KA", "XX", "PE");
		when(rs.getString("Namn")).thenReturn(" Per Eriksson ", " ", "Utan nummer", "Annan Per");
		doAnswer(invocation -> {
			final RowCallbackHandler handler = invocation.getArgument(1);
			for (var row = 0; row < 4; row++) {
				handler.processRow(rs);
			}
			return null;
		}).when(jdbc).query(anyString(), any(RowCallbackHandler.class));

		final var names = AlkolRepository.readUserNames(jdbc);

		assertThat(names.byNumber()).containsOnly(entry("7", "Per Eriksson"), entry("9", "Annan Per"));
		// The first user of a code counts
		assertThat(names.byCode()).containsOnly(entry("PE", "Per Eriksson"), entry("XX", "Utan nummer"));
	}
}
