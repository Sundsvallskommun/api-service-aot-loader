package se.sundsvall.aotloader.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileNamesTest {

	@Test
	void nameOf() {
		assertThat(FileNames.nameOf(" a.pdf ")).isEqualTo("a.pdf");
		assertThat(FileNames.nameOf("x/y\\z.pdf")).isEqualTo("z.pdf");
		assertThat(FileNames.nameOf("..")).isNull();
		assertThat(FileNames.nameOf("mapp/")).isNull();
		assertThat(FileNames.nameOf(" ")).isNull();
		assertThat(FileNames.nameOf(null)).isNull();
	}

	@Test
	void keyIsTheComposedNameInLowerCase() {
		assertThat(FileNames.key("C:\\mapp\\Tillsta\u030Andsbevis.PDF")).isEqualTo("tillst\u00E5ndsbevis.pdf");
		assertThat(FileNames.key("Tillst\u00E5ndsbevis.pdf")).isEqualTo(FileNames.key("TILLSTA\u030ANDSBEVIS.pdf"));
		assertThat(FileNames.key("..")).isNull();
		assertThat(FileNames.normalized(null)).isNull();
		assertThat(FileNames.normalized("Ritning A\u0308.pdf")).isEqualTo("ritning \u00E4.pdf");
	}
}
