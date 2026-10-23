package se.sundsvall.aotloader.service;

import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AttachedFileTest {

	@Test
	void attachedFiles() {
		final var file = new AttachedFile("Beslut.rtf", "ABC", 3);

		assertThat(file.isSameAs(new AttachedFile("beslut.RTF", "abc", 99))).isTrue();
		assertThat(file.isSameAs(new AttachedFile("beslut.rtf", "abd", 3))).isFalse();
		assertThat(file.isSameAs(new AttachedFile("annan.rtf", "abc", 3))).isFalse();
		assertThat(new AttachedFile("beslut.rtf", null, 3).isSameAs(new AttachedFile("beslut.rtf", "abc", 3))).isTrue();
		assertThat(new AttachedFile("beslut.rtf", null, 3).isSameAs(new AttachedFile("beslut.rtf", "abc", 4))).isFalse();
		assertThat(new AttachedFile("beslut.rtf", null, null).isSameAs(new AttachedFile("beslut.rtf", "abc", 3))).isFalse();
		assertThat(new AttachedFile(null, "abc", 3).isSameAs(new AttachedFile("beslut.rtf", "abc", 3))).isFalse();
		assertThat(AttachedFile.of(new ErrandAttachment().fileName("a.pdf").hash("h").fileSize(1))).isEqualTo(new AttachedFile("a.pdf", "h", 1));
		assertThat(AttachedFile.of(new AssetAttachment().fileName("a.pdf").fileSize(1))).isEqualTo(new AttachedFile("a.pdf", null, 1));
	}
}
