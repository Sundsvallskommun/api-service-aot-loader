package se.sundsvall.aotloader.integration.party;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.aotloader.support.SystemUnavailableException;
import se.sundsvall.dept44.problem.ThrowableProblem;

@Component
public class PartyIntegration {

	private static final Logger LOG = LoggerFactory.getLogger(PartyIntegration.class);

	/** The answers that tell that Party is busy or cannot be reached through the gateway, kept apart by the decoder. */
	public static final List<Integer> UNAVAILABLE_STATUSES = List.of(429, 503, 504);

	private final PartyClient client;

	PartyIntegration(final PartyClient client) {
		this.client = client;
	}

	/**
	 * Looks up the partyId of a number. The number is part of the path of the call, so no error from the call is passed
	 * on as it is: its message could hold the number (K6).
	 *
	 * @return                            the partyId, or empty when Party does not know the number
	 * @throws SystemUnavailableException when Party could not be reached: an error of the network, a timeout, a token
	 *                                    that could not be had, or Party or the gateway answering 429, 503 or 504
	 * @throws MigrationException         when Party answers the lookup with another error, as it does for an organization
	 *                                    number it lacks
	 */
	public Optional<String> getPartyId(final String municipalityId, final LegalId legalId) {
		try {
			return client.getPartyId(municipalityId, legalId.type().name(), legalId.number())
				.filter(partyId -> !partyId.isBlank());
		} catch (final RuntimeException e) {
			// The status of a problem, the class of any other error: the message may hold the number
			final var status = e instanceof final ThrowableProblem problem ? problem.getStatus() : null;
			final var cause = status != null ? String.valueOf(status) : e.getClass().getSimpleName();
			LOG.warn("Lookup of a {} number in Party failed: {}", legalId.type(), cause);
			if ((status != null && UNAVAILABLE_STATUSES.contains(status.value())) || SystemUnavailableException.isCauseOf(e)) {
				throw new SystemUnavailableException("Party gick inte att nå (%s); uppslaget görs om i nästa körning".formatted(cause));
			}
			throw new MigrationException("Uppslaget i Party misslyckades (%s)".formatted(cause));
		}
	}
}
