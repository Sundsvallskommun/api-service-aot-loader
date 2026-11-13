package se.sundsvall.aotloader.integration;

import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.assertj.core.api.Assertions.assertThat;

class FileRefusalTest {

	private static final ProblemErrorDecoder DECODER = new ProblemErrorDecoder("party-assets", List.copyOf(FileRefusal.STATUSES));

	@ParameterizedTest
	@ValueSource(ints = {
		413, 415, 422
	})
	void bothRefuseTheFileWithTheseStatuses(final int status) {
		final var problem = decode(status, "{\"title\":\"Fel\",\"status\":%d}".formatted(status));

		assertThat(FileRefusal.isRefusedBySupportManagement(problem)).isTrue();
		assertThat(FileRefusal.isRefusedByPartyAssets(problem)).isTrue();
	}

	@Test
	void partyAssetsRefusesAnInvalidFileWith400AsTheDecoderPassesItOn() {
		final var emptyFile = decode(400, "{\"title\":\"Invalid file\",\"status\":400,\"detail\":\"The uploaded file is empty\"}");
		final var longName = decode(400, "{\"title\":\"Invalid file name\",\"status\":400,\"detail\":\"The file name is too long\"}");

		assertThat(FileRefusal.isRefusedByPartyAssets(emptyFile)).isTrue();
		assertThat(FileRefusal.isRefusedByPartyAssets(longName)).isTrue();
		assertThat(FileRefusal.isRefusedBySupportManagement(emptyFile)).isFalse();
	}

	@Test
	void anotherBadRequestIsNoRefusal() {
		final var expired = decode(400, "{\"title\":\"Bad Request\",\"status\":400,\"detail\":\"Attachments cannot be added to an asset with status EXPIRED\"}");
		final var unreadable = decode(400, "{\"title\":\"Bad Request\",\"status\":400,\"detail\":\"Could not read input stream\"}");

		assertThat(FileRefusal.isRefusedByPartyAssets(expired)).isFalse();
		assertThat(FileRefusal.isRefusedByPartyAssets(unreadable)).isFalse();
		assertThat(FileRefusal.isRefusedBySupportManagement(unreadable)).isFalse();
	}

	@Test
	void aStatusTheDecoderDoesNotLetThroughIsNoRefusal() {
		final var problem = decode(500, "{}");

		assertThat(FileRefusal.isRefusedByPartyAssets(problem)).isFalse();
		assertThat(FileRefusal.isRefusedBySupportManagement(problem)).isFalse();
	}

	private static ThrowableProblem decode(final int status, final String body) {
		final var request = Request.create(Request.HttpMethod.POST, "http://localhost/2281/assets/1/attachments", Map.of(), null, StandardCharsets.UTF_8, null);
		final var response = Response.builder().status(status).reason("Svar").request(request).headers(Map.of()).body(body, StandardCharsets.UTF_8).build();
		return (ThrowableProblem) DECODER.decode("PartyAssetsClient#createAttachment", response);
	}
}
