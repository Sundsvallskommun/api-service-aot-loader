package se.sundsvall.aotloader.support;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextsTest {

	@Test
	void trimToNull() {
		assertThat(Texts.trimToNull("  a b  ")).isEqualTo("a b");
		assertThat(Texts.trimToNull("   ")).isNull();
		assertThat(Texts.trimToNull(null)).isNull();
		assertThat(Texts.isBlank(" ")).isTrue();
		assertThat(Texts.isBlank("x")).isFalse();
	}

	@Test
	void truncate() {
		assertThat(Texts.truncate(null, 5, null)).isNull();
		assertThat(Texts.truncate("abcde", 5, null)).isEqualTo("abcde");
		assertThat(Texts.truncate("abcdef", 5, null)).isEqualTo("abcd…").hasSize(5);
		assertThat(Texts.truncate("abc defgh", 8, " (mer)")).isEqualTo("ab (mer)").hasSize(8);
		assertThat(Texts.truncate("a".repeat(300), 255, " … (se ärendet)")).hasSize(255).endsWith(" … (se ärendet)");
		assertThatThrownBy(() -> Texts.truncate("abcdefgh", 3, "...")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void split() {
		assertThat(Texts.split(null, 10)).isEmpty();
		assertThat(Texts.split("  ", 10)).isEmpty();
		assertThat(Texts.split("kort", 10)).containsExactly("kort");
		assertThat(Texts.split("aaaa bbbb cccc", 10)).containsExactly("aaaa bbbb", "cccc");
		assertThat(Texts.split("a".repeat(25), 10)).containsExactly("a".repeat(10), "a".repeat(10), "a".repeat(5));

		final var long6000 = ("ord ".repeat(1500)).strip();
		final var parts = Texts.split(long6000, 3000);
		assertThat(parts).hasSize(2).allSatisfy(part -> assertThat(part.length()).isLessThanOrEqualTo(3000));
		assertThat(String.join(" ", parts)).isEqualTo(long6000);
	}

	@Test
	void join() {
		assertThat(Texts.join(", ", Arrays.asList(" a ", null, " ", "b"))).isEqualTo("a, b");
		assertThat(Texts.join(", ", List.of())).isNull();
		assertThat(Texts.joinWords("a", null, " b ")).isEqualTo("a b");
		assertThat(Texts.joinWords(null, " ")).isNull();
	}

	@Test
	void paragraphs() {
		assertThat(Texts.paragraphs("Tillsyn, migrerat från AlkT.\nDiarienummer: IAN-1\r\n\n  \nVillkor:\n- Bordsservering & <mat>\n")).isEqualTo(
			"<p>Tillsyn, migrerat från AlkT.</p><p>Diarienummer: IAN-1</p><p>Villkor:</p><p>- Bordsservering &amp; &lt;mat&gt;</p>");
		assertThat(Texts.paragraphs("Citat \"så\" och 'så'")).isEqualTo("<p>Citat &quot;så&quot; och &#39;så&#39;</p>");
		assertThat(Texts.paragraphs(" \n ")).isNull();
		assertThat(Texts.paragraphs(null)).isNull();
	}

	@Test
	void boldParagraph() {
		assertThat(Texts.boldParagraph("Beslut & <villkor>")).isEqualTo("<p><strong>Beslut &amp; &lt;villkor&gt;</strong></p>");
	}

	@Test
	void titleAndRange() {
		assertThat(Texts.title("Serveringstillstånd", "Krogen")).isEqualTo("Serveringstillstånd – Krogen");
		assertThat(Texts.range("08:00", "01:00")).isEqualTo("08:00–01:00");
		assertThat(Texts.range("2026-05-01", null)).isEqualTo("2026-05-01–");
		assertThat(Texts.range(null, null)).isEqualTo("–");
	}

	@Test
	void dateRange() {
		// As Draken writes a period of days
		assertThat(Texts.dateRange("2026-05-01", "2026-09-30")).isEqualTo("2026-05-01 – 2026-09-30");
		assertThat(Texts.dateRange("2026-05-01", null)).isEqualTo("2026-05-01 –");
		assertThat(Texts.dateRange(null, "09-30")).isEqualTo("– 09-30");
		assertThat(Texts.dateRange(null, null)).isEqualTo("–");
	}

	@Test
	void withoutBlanks() {
		assertThat(Texts.withoutBlanks(" 852 30 ")).isEqualTo("85230");
		assertThat(Texts.withoutBlanks("852 30")).isEqualTo("85230");
		assertThat(Texts.withoutBlanks(" \t ")).isNull();
		assertThat(Texts.withoutBlanks(null)).isNull();
	}
}
