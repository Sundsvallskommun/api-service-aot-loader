package se.sundsvall.aotloader.support;

import java.net.URI;
import java.util.Optional;
import org.springframework.http.ResponseEntity;

/**
 * Reads the id of a created resource from the Location header of the response.
 */
public final class Locations {

	private Locations() {}

	/**
	 * @param  response           the response to a create call
	 * @param  what               what was created, for the message when the id is missing, e.g. "ärendet"
	 * @return                    the last segment of the path in the Location header
	 * @throws MigrationException when the response has no Location header or the header has no id
	 */
	public static String idOf(final ResponseEntity<?> response, final String what) {
		return Optional.ofNullable(response)
			.map(r -> r.getHeaders().getLocation())
			.map(URI::getPath)
			.map(path -> path.endsWith("/") ? path.substring(0, path.length() - 1) : path)
			.map(path -> path.substring(path.lastIndexOf('/') + 1))
			.filter(id -> !id.isBlank())
			.orElseThrow(() -> new MigrationException("Svaret när " + what + " skapades saknade id i Location"));
	}
}
