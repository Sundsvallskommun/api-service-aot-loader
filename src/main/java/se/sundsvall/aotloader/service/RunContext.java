package se.sundsvall.aotloader.service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.files.FileArea;

/**
 * What the items of one run share. The partyIds found are kept for the run only, in memory, never stored (K6).
 */
public final class RunContext {

	private final String runId;
	private final String municipalityId;
	private final boolean dryRun;
	private final Map<String, Map<String, String>> labelIds;
	private final Integer limit;
	private final AtomicBoolean stopRequested;
	private final Function<Source, FileArea> fileAreaFactory;
	private final Map<Source, FileArea> fileAreas = new EnumMap<>(Source.class);
	private final Map<String, Optional<String>> partyIds = new HashMap<>();
	private final Map<ItemKey, Outcome> outcomes = new HashMap<>();
	private int writtenItems;

	/**
	 * @param runId           the run
	 * @param municipalityId  the municipality the run is started for, which an item belongs to unless its area belongs to
	 *                        another (decision 19)
	 * @param dryRun          true for a dry run, which writes nothing to the target systems or the status table
	 * @param labelIds        the ids of the labels by normalized resource path, per municipality
	 * @param limit           the largest number of items the run writes, null for no limit
	 * @param stopRequested   set when the run is to stop before its next item
	 * @param fileAreaFactory creates the file area of a source, once per run
	 */
	public RunContext(final String runId, final String municipalityId, final boolean dryRun, final Map<String, Map<String, String>> labelIds, final Integer limit, final AtomicBoolean stopRequested,
		final Function<Source, FileArea> fileAreaFactory) {
		this.runId = runId;
		this.municipalityId = municipalityId;
		this.dryRun = dryRun;
		this.labelIds = Map.copyOf(labelIds);
		this.limit = limit;
		this.stopRequested = stopRequested;
		this.fileAreaFactory = fileAreaFactory;
	}

	public String runId() {
		return runId;
	}

	public String municipalityId() {
		return municipalityId;
	}

	public boolean dryRun() {
		return dryRun;
	}

	/**
	 * @return the ids of the labels in the municipality, empty when they were not read for it
	 */
	public Map<String, String> labelIds(final String municipalityId) {
		return labelIds.getOrDefault(municipalityId, Map.of());
	}

	public boolean stopRequested() {
		return stopRequested.get();
	}

	public FileArea fileArea(final Source source) {
		return fileAreas.computeIfAbsent(source, fileAreaFactory);
	}

	/**
	 * Gives the partyId of a number in a municipality, looking it up the first time the run asks for it. A failed lookup
	 * is not remembered.
	 *
	 * @param  municipalityId the municipality Party is asked for
	 * @param  legalId        the number
	 * @param  lookup         looks the number up in Party
	 * @return                the partyId, empty when Party does not know the number
	 */
	public Optional<String> partyId(final String municipalityId, final LegalId legalId, final Function<LegalId, Optional<String>> lookup) {
		final var key = cacheKey(municipalityId, legalId);
		final var cached = partyIds.get(key);
		if (cached != null) {
			return cached;
		}
		final var found = lookup.apply(legalId);
		partyIds.put(key, found);
		return found;
	}

	/**
	 * @return the outcome of the item earlier in this run, empty when the run has not taken it
	 */
	public Optional<Outcome> outcomeOf(final ItemKey key) {
		return Optional.ofNullable(outcomes.get(key));
	}

	public void recordOutcome(final ItemKey key, final Outcome outcome) {
		outcomes.put(key, outcome);
	}

	/**
	 * Counts an item that is about to be written, unless the limit is reached.
	 *
	 * @return true when the item may be written
	 */
	public boolean tryCountWrite() {
		if (limit != null && writtenItems >= limit) {
			return false;
		}
		writtenItems++;
		return true;
	}

	private static String cacheKey(final String municipalityId, final LegalId legalId) {
		return municipalityId + ":" + legalId.type() + ":" + legalId.number();
	}
}
