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
}
