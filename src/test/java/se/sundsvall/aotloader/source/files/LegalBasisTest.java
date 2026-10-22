package se.sundsvall.aotloader.source.files;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class LegalBasisTest {

	private static byte[] rtf(final String content) {
		return content.getBytes(Charset.forName("windows-1252"));
	}

	@Test
	void theLegalBasisADecisionStates() {
		final var decision = rtf("""
			{\\rtf1\\ansi\\ansicpg1252{\\fonttbl{\\f0 Times New Roman;}}{\\colortbl;\\red0\\green0\\blue0;}
			{\\*\\generator Microsoft Word;}{\\info{\\title Beslut 8 kap. 1 \\'a7 alkohollagen (2010:1622)}}
			\\pard\\plain\\f0 Kommunen beviljar serveringstillst\\'e5nd enligt 8 kap. 2 \\'a7 alkohollagen (2010:1622).\\par
			{\\pict\\pngblip 89504e470d0a 9 kap. 1 \\'a7 lagen (2010:1622)}{\\object{\\*\\objdata 0105 3 kap. 4 \\'a7 (2000:1)}}
			}""");

		assertThat(LegalBasis.of(decision)).hasValue("8 kap. 2 § alkohollagen (2010:1622)");
	}

	@Test
	void theLawOfAnAppealIsNoLegalBasis() {
		final var decision = rtf("""
			{\\rtf1\\ansi\\pard Kommunen beviljar tillst\\'e5nd enligt 5 kap. 1 \\'a7 lagen om tobak och liknande produkter (2018:2088).\\par
			Hur man \\'f6verklagar: beslutet kan \\'f6verklagas enligt 9 kap. 2 \\'a7 lagen (2018:2088).\\par
			OVERKLAGANDE skickas enligt 9 kap. 3 \\'a7 lagen (2018:2088)\\par}""");

		assertThat(LegalBasis.of(decision)).hasValue("5 kap. 1 § lagen om tobak och liknande produkter (2018:2088)");
	}

	@Test
	void theLawOfAPermitDecidedUnderTheEarlierLaw() {
		final var decision = rtf("{\\rtf1 Tillst\\'e5nd enligt 7 kap. 5 \\'a7 alkohollagen (1994:1738)}");

		assertThat(LegalBasis.of(decision)).hasValue("7 kap. 5 § alkohollagen (1994:1738)");
	}

	@Test
	void eachReferenceOnceInTheOrderTheDocumentStatesThem() {
		final var certificate = rtf("""
			{\\rtf1 Tillst\\'e5nd enligt 5 kap 1 \\'a7 Lag om tobak och liknande produkter (2018:2088)\\par
			och 8 kap.\\~2\\'a7\\tab alkohollagen\\line (2010:1622); upprepat 5 kap 1 \\'a7 Lag om tobak och liknande produkter (2018:2088)}""");

		assertThat(LegalBasis.of(certificate)).hasValue("5 kap 1 § Lag om tobak och liknande produkter (2018:2088); 8 kap. 2 § alkohollagen (2010:1622)");
	}

	@Test
	void unicodeCharactersAndTheirFallback() {
		final var decision = rtf("{\\rtf1\\uc1 Enligt 8 kap. 2 \\u167\\'a7 alkohollagen (2010:1622) och 4 kap. 3 a \\u167? lagen (2010:1622)\\u-3913x}");

		assertThat(LegalBasis.of(decision)).hasValue("8 kap. 2 § alkohollagen (2010:1622); 4 kap. 3 a § lagen (2010:1622)");
	}

	@Test
	void escapedBracesAndBackslashesAreText() {
		final var decision = rtf("{\\rtf1 \\{8 kap. 2 \\'a7 alkohollagen (2010:1622)\\} \\\\ {\\*\\bkmkstart a\\}b} {\\b fet}}");

		assertThat(LegalBasis.text(new String(decision, StandardCharsets.ISO_8859_1))).isEqualTo("{8 kap. 2 § alkohollagen (2010:1622)} \\  fet");
		assertThat(LegalBasis.of(decision)).hasValue("8 kap. 2 § alkohollagen (2010:1622)");
	}

	@Test
	void aNonBreakingSpaceIsASpace() {
		final var decision = rtf("{\\rtf1 Enligt 8\\'a0kap.\\'a02\\'a0\\'a7 alkohollagen (2010:1622) och 4\\u160\\'a0kap. 3 \\'a7 lagen (2010:1622)}");

		assertThat(LegalBasis.of(decision)).hasValue("8 kap. 2 § alkohollagen (2010:1622); 4 kap. 3 § lagen (2010:1622)");
	}

	@Test
	void aLongNumberIsSearchedInLinearTime() {
		final var decision = rtf("{\\rtf1 " + "1".repeat(200_000) + " 8 kap. 2\\'a7alkohollagen (2010:1622)}");

		assertThat(assertTimeoutPreemptively(Duration.ofSeconds(5), () -> LegalBasis.of(decision))).hasValue("8 kap. 2 § alkohollagen (2010:1622)");
	}

	@Test
	void aDocumentThatStatesNoLegalBasis() {
		assertThat(LegalBasis.of(rtf("{\\rtf1 Beslut om serveringstillst\\'e5nd}"))).isEmpty();
		// A paragraph without the number of the law in the statute book is no reference
		assertThat(LegalBasis.of(rtf("{\\rtf1 enligt 8 kap. 2 \\'a7 alkohollagen}"))).isEmpty();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {
		"8 kap. 2 § alkohollagen (2010:1622)", "%PDF-1.4 8 kap. 2 § alkohollagen (2010:1622)", "{\\rtf"
	})
	void onlyAnRtfDocumentIsRead(final String content) {
		assertThat(LegalBasis.of(content == null ? null : content.getBytes(StandardCharsets.UTF_8))).isEmpty();
	}

	@Test
	void aDocumentThatEndsInTheMiddleOfAControlWord() {
		assertThat(LegalBasis.of(rtf("{\\rtf1 8 kap. 2 \\'a7 alkohollagen (2010:1622) {\\pict 0a"))).hasValue("8 kap. 2 § alkohollagen (2010:1622)");
		assertThat(LegalBasis.of(rtf("{\\rtf1 8 kap. 2 \\'a7 alkohollagen (2010:1622) \\'a"))).hasValue("8 kap. 2 § alkohollagen (2010:1622)");
		assertThat(LegalBasis.of(rtf("{\\rtf1 8 kap. 2 \\'a7 alkohollagen (2010:1622) \\u"))).hasValue("8 kap. 2 § alkohollagen (2010:1622)");
		assertThat(LegalBasis.of(rtf("{\\rtf1 8 kap. 2 \\'a7 alkohollagen (2010:1622) \\'zz\\"))).hasValue("8 kap. 2 § alkohollagen (2010:1622)");
	}
}
