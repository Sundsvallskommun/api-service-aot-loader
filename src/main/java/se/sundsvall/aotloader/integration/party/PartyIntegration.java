package se.sundsvall.aotloader.integration.party;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.dept44.problem.ThrowableProblem;

@Component
public class PartyIntegration {

	private static final Logger LOG = LoggerFactory.getLogger(PartyIntegration.class);

	private final PartyClient client;

	PartyIntegration(final PartyClient client) {
		this.client = client;
	}

	/**
	 * Looks up the partyId of a number. The number is part of the path of the call, so no error from the call is passed
	 * on as it is: its message could hold the number (K6).
	 *
	 * @return                    the partyId, or empty when Party does not know the number
	 * @throws MigrationException when the lookup fails
	 */
	public Optional<String> getPartyId(final String municipalityId, final LegalId legalId) {
		try {
			return client.getPartyId(municipalityId, legalId.type().name(), legalId.number())
				.filter(partyId -> !partyId.isBlank());
		} catch (final RuntimeException e) {
			// The status of a problem, the class of any other error: the message may hold the number
			final var cause = e instanceof final ThrowableProblem problem ? String.valueOf(problem.getStatus()) : e.getClass().getSimpleName();
			LOG.warn("Lookup of a {} number in Party failed: {}", legalId.type(), cause);
			throw new MigrationException("Uppslaget i Party misslyckades (%s)".formatted(cause));
		}
	}
}
