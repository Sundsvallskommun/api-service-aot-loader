package se.sundsvall.aotloader.service.model;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ItemSelectionTest {

	private static final ItemKey PERMIT = new ItemKey(Source.ALKT, Kind.TILLSTAND, "1");
	private static final ItemKey NOTIFICATION = new ItemKey(Source.ALKOL, Kind.ANMALAN, "40-FOLKOL");

	@Test
	void aKeyIsReadAsItIsWritten() {
		assertThat(ItemKey.parse(NOTIFICATION.toString())).isEqualTo(NOTIFICATION);
		assertThat(ItemKey.parse(" alkt/tillsyn/700 ")).isEqualTo(new ItemKey(Source.ALKT, Kind.TILLSYN, "700"));
		assertThat(ItemKey.parse("ALKT/TILLSTAND/utan-id-1")).isEqualTo(new ItemKey(Source.ALKT, Kind.TILLSTAND, "utan-id-1"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"", "ALKT/TILLSTAND", "ALKT/TILLSTAND/", "ALKT/TILLSTAND/1/2", "ALKT-TILLSTAND-1", "ALKT/TILLSTAND/1,2", "ALKT/TILLSTAND/1 2", "TOBAK/TILLSTAND/1", "ALKT/BESLUT/1"
	})
	void aKeyThatIsNotSourceKindAndIdIsRefused(final String text) {
		assertThatIllegalArgumentException().isThrownBy(() -> ItemKey.parse(text));
	}

	@Test
	void aSelectionKeepsEachItemOnceInTheOrderAskedForAndIsKeptAsText() {
		final var selection = ItemSelection.of(List.of(NOTIFICATION, PERMIT, NOTIFICATION));

		assertThat(selection.keys()).containsExactly(NOTIFICATION, PERMIT);
		assertThat(selection.text()).isEqualTo("ALKOL/ANMALAN/40-FOLKOL,ALKT/TILLSTAND/1");
		assertThat(ItemSelection.parse(selection.text())).isEqualTo(selection);
		assertThat(selection.isAll()).isFalse();
		assertThat(selection.includes(PERMIT)).isTrue();
		assertThat(selection.includes(new ItemKey(Source.ALKT, Kind.TILLSTAND, "2"))).isFalse();
		assertThat(selection.missing(Set.of(PERMIT))).containsExactly(NOTIFICATION);
	}

	@Test
	void noSelectionIsEveryItem() {
		assertThat(ItemSelection.parse(null)).isEqualTo(ItemSelection.all());
		assertThat(ItemSelection.parse(" ")).isEqualTo(ItemSelection.all());
		assertThat(ItemSelection.all().text()).isNull();
		assertThat(ItemSelection.all().includes(PERMIT)).isTrue();
		assertThat(ItemSelection.all().missing(Set.of())).isEmpty();
	}

	@Test
	void aMessageListsTwentyItemsAndCountsTheRest() {
		final var keys = IntStream.rangeClosed(1, 22).mapToObj(id -> new ItemKey(Source.ALKT, Kind.TILLSTAND, String.valueOf(id))).toList();

		assertThat(ItemSelection.listText(keys)).startsWith("ALKT/TILLSTAND/1, ALKT/TILLSTAND/2, ").endsWith("ALKT/TILLSTAND/20 och 2 till");
		assertThat(ItemSelection.listText(List.of(PERMIT, NOTIFICATION))).isEqualTo("ALKT/TILLSTAND/1, ALKOL/ANMALAN/40-FOLKOL");
	}
}
