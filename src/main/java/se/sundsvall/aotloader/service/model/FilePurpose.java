package se.sundsvall.aotloader.service.model;

import static java.util.Objects.requireNonNull;

/**
 * An attachment purpose of the namespace, as SupportManagement has it.
 *
 * @param id          the id, which differs between environments
 * @param name        the name, e.g. REFERRAL_POLICE_REQUEST
 * @param displayName the name shown, which a file on a post in PartyAssets gets as its category, as pw-alkt does
 */
public record FilePurpose(String id, String name, String displayName) {

	public FilePurpose {
		requireNonNull(id, "id");
		requireNonNull(name, "name");
	}
}
