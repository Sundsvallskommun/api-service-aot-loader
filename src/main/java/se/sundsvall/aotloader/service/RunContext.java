package se.sundsvall.aotloader.service;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import se.sundsvall.aotloader.service.model.FilePurpose;
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
	private final Map<String, Map<String, FilePurpose>> purposes;
	private final Integer limit;
	private final AtomicBoolean stopRequested;
	private final Function<Source, FileArea> fileAreaFactory;
	private final Map<Source, FileArea> fileAreas = new EnumMap<>(Source.class);
	private final Map<String, Optional<String>> partyIds = new HashMap<>();
	private final Map<ItemKey, Outcome> outcomes = new HashMap<>();
	private int writtenItems;

	/**
	 * @param runId           the run
	 * @param municipalityId  the municipality the run is started for, which every item is written to (decision 19)
	 * @param dryRun          true for a dry run, which writes nothing to the target systems or the status table
	 * @param check           what the checks before the run found: the ids of the labels by normalized resource path
	 *                        and the attachment purposes a file can get by name, per municipality
	 * @param limit           the largest number of items the run writes, null for no limit
	 * @param stopRequested   set when the run is to stop before its next item
	 * @param fileAreaFactory creates the file area of a source, once per run
	 */
	public RunContext(final String runId, final String municipalityId, final boolean dryRun, final Preflight.Result check, final Integer limit,
		final AtomicBoolean stopRequested, final Function<Source, FileArea> fileAreaFactory) {
		this.runId = runId;
		this.municipalityId = municipalityId;
		this.dryRun = dryRun;
		this.labelIds = check.labelIds();
		this.purposes = check.purposes();
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

	/**
	 * @return the attachment purpose of the name in the municipality, empty when it was not read for it
	 */
	public Optional<FilePurpose> purpose(final String municipalityId, final String name) {
		return Optional.ofNullable(purposes.getOrDefault(municipalityId, Map.of()).get(name));
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
		return partyIds.computeIfAbsent(cacheKey(municipalityId, legalId), _ -> lookup.apply(legalId));
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
