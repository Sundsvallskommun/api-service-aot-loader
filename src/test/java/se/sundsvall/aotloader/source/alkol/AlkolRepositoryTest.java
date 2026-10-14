package se.sundsvall.aotloader.source.alkol;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AlkolRepositoryTest {

	@Test
	void theTimeOfAVisitIsTheTimeOfDayAndMidnightIsNoTime() throws SQLException {
		final var rs = mock(ResultSet.class);
		when(rs.getTimestamp("StartTid")).thenReturn(Timestamp.valueOf("1899-12-30 14:05:00"));
		when(rs.getTimestamp("SlutTid")).thenReturn(Timestamp.valueOf("1899-12-30 00:00:00"));

		assertThat(AlkolRepository.time(rs, "StartTid")).isEqualTo("14:05");
		assertThat(AlkolRepository.time(rs, "SlutTid")).isNull();
		assertThat(AlkolRepository.time(rs, "Saknas")).isNull();
	}
}
