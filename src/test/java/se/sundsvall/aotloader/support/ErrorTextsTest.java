package se.sundsvall.aotloader.support;

import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

class ErrorTextsTest {

	@Test
	void theOwnMessageOfTheMigrationIsKept() {
		assertThat(ErrorTexts.describe(new MigrationException("Eget fel"))).isEqualTo("Eget fel");
	}

	@Test
	void aProblemIsDescribedByItsStatusAndDetailWithNumbersMasked() {
		assertThat(ErrorTexts.describe(Problem.valueOf(BAD_REQUEST, "Fel för 198001011234"))).isEqualTo("400 BAD_REQUEST Fel för [nummer]");
	}

	@Test
	void anyOtherErrorIsDescribedByItsClassAndMessage() {
		assertThat(ErrorTexts.describe(new IllegalStateException("Hoppsan"))).isEqualTo("IllegalStateException: Hoppsan");
		assertThat(ErrorTexts.describe(new NullPointerException())).isEqualTo("NullPointerException");
		assertThat(ErrorTexts.describe(new OutOfMemoryError("Java heap space"))).isEqualTo("OutOfMemoryError: Java heap space");
	}

	@Test
	void theCopyForTheLogKeepsTheStackTraceAndCausesWithTheirMessagesDescribed() {
		final var cause = new IllegalArgumentException("Fel för 198001011234");
		final var error = new IllegalStateException("Hoppsan anna@example.com", cause);

		final var copy = ErrorTexts.forLog(error);

		assertThat(copy).hasMessage("java.lang.IllegalStateException: IllegalStateException: Hoppsan [e-post]");
		assertThat(copy.getStackTrace()).isEqualTo(error.getStackTrace());
		assertThat(copy.getCause()).hasMessage("java.lang.IllegalArgumentException: IllegalArgumentException: Fel för [nummer]").hasNoCause();
		assertThat(copy.getCause().getStackTrace()).isEqualTo(cause.getStackTrace());
		assertThat(ErrorTexts.forLog(new MigrationException("Eget fel"))).hasMessage(MigrationException.class.getName() + ": Eget fel");
	}

	@Test
	void theCopyForTheLogEndsALongChainOfCauses() {
		Throwable error = new IllegalStateException("0");
		for (var i = 1; i <= 20; i++) {
			error = new IllegalStateException(String.valueOf(i), error);
		}

		var copy = ErrorTexts.forLog(error);
		var depth = 0;
		while (copy.getCause() != null) {
			copy = copy.getCause();
			depth++;
		}
		assertThat(depth).isEqualTo(10);
	}

	@Test
	void theQueryOfAUrlIsLeftOut() {
		assertThat(ErrorTexts.describe(new IllegalStateException("Read timed out executing GET http://host/party-assets/2281/assets?partyId=a&assetId=b#c and more")))
			.isEqualTo("IllegalStateException: Read timed out executing GET http://host/party-assets/2281/assets and more");
		assertThat(ErrorTexts.describe(Problem.valueOf(BAD_REQUEST, "Fel vid https://host/x?filter=y"))).isEqualTo("400 BAD_REQUEST Fel vid https://host/x");
		assertThat(ErrorTexts.describe(new IllegalStateException("Frågan? Svar"))).isEqualTo("IllegalStateException: Frågan? Svar");
	}
}
