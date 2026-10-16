package se.sundsvall.aotloader.integration.supportmanagement.configuration;

import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.assertj.core.api.Assertions.assertThat;

class SupportManagementConfigurationTest {

	@Test
	void theAnswersTheMigrationTellsApartKeepTheirStatusAndOthersBecome502() {
		final var decoder = new ProblemErrorDecoder(SupportManagementConfiguration.CLIENT_ID, SupportManagementConfiguration.bypassedStatuses());

		assertThat(statusOf(decoder, 404)).isEqualTo(404);
		assertThat(statusOf(decoder, 400)).isEqualTo(400);
		assertThat(statusOf(decoder, 413)).isEqualTo(413);
		assertThat(statusOf(decoder, 415)).isEqualTo(415);
		assertThat(statusOf(decoder, 422)).isEqualTo(422);
		assertThat(statusOf(decoder, 409)).isEqualTo(502);
		assertThat(statusOf(decoder, 500)).isEqualTo(502);
	}

	private static int statusOf(final ProblemErrorDecoder decoder, final int status) {
		final var request = Request.create(Request.HttpMethod.POST, "http://localhost/2281/AOT/errands/1/attachments", Map.of(), null, StandardCharsets.UTF_8, null);
		final var response = Response.builder().status(status).reason("Svar").request(request).headers(Map.of()).body("{}", StandardCharsets.UTF_8).build();
		final var exception = decoder.decode("SupportManagementClient#createAttachment", response);
		assertThat(exception).isInstanceOf(ThrowableProblem.class);
		return ((ThrowableProblem) exception).getStatus().value();
	}
}
