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
}
