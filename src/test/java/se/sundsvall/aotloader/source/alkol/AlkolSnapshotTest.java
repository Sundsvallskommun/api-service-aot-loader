package se.sundsvall.aotloader.source.alkol;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.PermitViewRow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class AlkolSnapshotTest {

	@Test
	void theRowsOfASalesPlaceAreMergedAndRowsWithoutPlaceLeftOut() {
		final var rows = Arrays.asList(
			new PermitViewRow(2, false, false, true, false),
			new PermitViewRow(1, true, false, false, false),
			new PermitViewRow(null, true, true, true, true),
			new PermitViewRow(1, false, true, false, true),
			new PermitViewRow(1, false, false, false, false));

		assertThat(PermitViewRow.bySalesPlace(rows)).containsExactly(
			entry(2, new PermitViewRow(2, false, false, true, false)),
			entry(1, new PermitViewRow(1, true, true, false, true)));
	}

	@Test
	void aPlaceWithOneRowKeepsItAndNoRowsGiveNoPlaces() {
		final var row = new PermitViewRow(3, true, true, true, true);

		assertThat(PermitViewRow.bySalesPlace(List.of(row))).containsExactly(entry(3, row));
		assertThat(PermitViewRow.bySalesPlace(List.of())).isEmpty();
	}

	@Test
	void aKindSetByAnEarlierRowStaysSet() {
		final var rows = List.of(
			new PermitViewRow(1, true, true, true, true),
			new PermitViewRow(1, false, false, false, false));

		assertThat(PermitViewRow.bySalesPlace(rows)).containsExactly(entry(1, new PermitViewRow(1, true, true, true, true)));
	}
}
