package se.sundsvall.aotloader.service.model;

import java.util.List;

/**
 * What a reader found in its source.
 *
 * @param candidates the items to migrate or review
 * @param notices    rows that are not migrated but are listed in the report, e.g. an active sales place without any
 *                   product
 */
public record ReaderResult(List<Candidate> candidates, List<Notice> notices) {

	public ReaderResult {
		candidates = candidates == null ? List.of() : List.copyOf(candidates);
		notices = notices == null ? List.of() : List.copyOf(notices);
	}

	/**
	 * A row of the report about something in the source that is not an item of the migration.
	 *
	 * @param key       the identity the row is listed under
	 * @param placeName the name of the place
	 * @param outcome   the outcome listed
	 * @param rule      the code of the rule or notice, as the column of the rules shows it, e.g. G1 or Ö1
	 * @param detail    what was found, never names or numbers of persons or companies
	 */
	public record Notice(ItemKey key, String placeName, Outcome outcome, String rule, String detail) {
	}
}
