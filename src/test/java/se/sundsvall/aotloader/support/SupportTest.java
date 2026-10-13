package se.sundsvall.aotloader.support;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupportTest {

	@Test
	void filterValues() {
		assertThat(FilterValues.isSafe("ALKT-TILLSTAND-1")).isTrue();
		assertThat(FilterValues.isSafe("11111111-1111-1111-1111-111111111111")).isTrue();
		assertThat(FilterValues.isSafe("a' or 'b")).isFalse();
		assertThat(FilterValues.isSafe("åäö")).isFalse();
		assertThat(FilterValues.isSafe("")).isFalse();
		assertThat(FilterValues.isSafe(null)).isFalse();
	}

	@Test
	void piiScrubberMasksNumbersAndEmailAddresses() {
		assertThat(PiiScrubber.scrub("Fel för 198001011234 och 556677-8899 och 800101+1234, kontakt anna.svensson@example.com"))
			.isEqualTo("Fel för [nummer] och [nummer] och [nummer], kontakt [e-post]");
		assertThat(PiiScrubber.scrub("Ärende 2026-10-12 med 8 siffror 12345678 och id 123")).isEqualTo("Ärende 2026-10-12 med 8 siffror 12345678 och id 123");
		assertThat(PiiScrubber.scrub("Ärendet 5f79a808-0ef3-4985-99b9-123456789012 finns inte")).isEqualTo("Ärendet 5f79a808-0ef3-4985-99b9-123456789012 finns inte");
		assertThat(PiiScrubber.scrub("Ärendena 12345678-1234-4985-9999-123456789012 och 5f79a808-0ef3-4985-99b9-123456789013"))
			.isEqualTo("Ärendena 12345678-1234-4985-9999-123456789012 och 5f79a808-0ef3-4985-99b9-123456789013");
		assertThat(PiiScrubber.scrub("Filer: intyg-19800101-1234.pdf, Pnr198001011234.doc, bolag_5566778899.msg, 1980010112345f79a808-0ef3-4985-99b9-123456789012"))
			.isEqualTo("Filer: intyg-[nummer].pdf, Pnr[nummer].doc, bolag_[nummer].msg, [nummer]5f79a808-0ef3-4985-99b9-123456789012");
		assertThat(PiiScrubber.scrub("Tidpunkt 20261012083000 och telefon 060-123456")).isEqualTo("Tidpunkt 20261012083000 och telefon 060-123456");
		assertThat(PiiScrubber.scrub(null)).isNull();
		assertThat(PiiScrubber.scrub("x".repeat(5000))).hasSize(5000);
	}

	@Test
	void diaryNumbers() {
		assertThat(DiaryNumbers.normalize(" ian-2019 12345 ")).isEqualTo("IAN201912345");
		assertThat(DiaryNumbers.normalize(" - ")).isNull();
		assertThat(DiaryNumbers.normalize(null)).isNull();
	}

	@Test
	void locations() {
		assertThat(Locations.idOf(ResponseEntity.created(URI.create("/2281/AOT/errands/abc-123")).build(), "ärendet")).isEqualTo("abc-123");
		assertThat(Locations.idOf(ResponseEntity.created(URI.create("http://host/2281/assets/xyz/")).build(), "tillståndet")).isEqualTo("xyz");
		assertThatThrownBy(() -> Locations.idOf(ResponseEntity.ok().build(), "ärendet"))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Svaret när ärendet skapades saknade id i Location");
		assertThatThrownBy(() -> Locations.idOf(null, "relationen")).isInstanceOf(MigrationException.class);
	}

	@Test
	void byteArrayMultipartFile() throws Exception {
		final var content = "innehåll".getBytes(StandardCharsets.UTF_8);
		final var file = new ByteArrayMultipartFile("errandAttachment", "beslut.pdf", content);
		content[0] = 0;

		assertThat(file.getName()).isEqualTo("errandAttachment");
		assertThat(file.getOriginalFilename()).isEqualTo("beslut.pdf");
		assertThat(file.getContentType()).isEqualTo("application/pdf");
		assertThat(file.isEmpty()).isFalse();
		assertThat(file.getSize()).isEqualTo(9);
		assertThat(new String(file.getBytes(), StandardCharsets.UTF_8)).isEqualTo("innehåll");
		assertThat(new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("innehåll");
		assertThat(new ByteArrayMultipartFile("p", "mejl.msg", new byte[0]).getContentType()).isEqualTo("application/octet-stream");
		assertThat(new ByteArrayMultipartFile("p", "tom.txt", new byte[0]).isEmpty()).isTrue();
		assertThatThrownBy(() -> file.transferTo(new java.io.File("x"))).isInstanceOf(UnsupportedOperationException.class);
	}
}
