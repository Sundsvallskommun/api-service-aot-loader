package se.sundsvall.aotloader.service;

import generated.se.sundsvall.partyassets.Status;
import generated.se.sundsvall.supportmanagement.Errand.LifecycleEnum;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.db.MigrationItemRepository;
import se.sundsvall.aotloader.integration.db.ReviewExceptionRepository;
import se.sundsvall.aotloader.integration.db.model.MigrationItemEntity;
import se.sundsvall.aotloader.integration.db.model.ReviewExceptionEntity;
import se.sundsvall.aotloader.service.Assessment.Decision;
import se.sundsvall.aotloader.service.RelationPlanner.RelationPlan;
import se.sundsvall.aotloader.service.TargetWriter.PostLookup;
import se.sundsvall.aotloader.service.mapper.TargetMapper;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ItemStatus;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.PartyIds;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.service.model.Step;
import se.sundsvall.aotloader.support.ErrorTexts;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.ERRAND_NUMBER_MAX_LENGTH;
import static se.sundsvall.aotloader.Constants.PLACE_NAME_MAX_LENGTH;
import static se.sundsvall.aotloader.Constants.PLACE_NUMBER_MAX_LENGTH;
import static se.sundsvall.aotloader.service.model.ItemStatus.ARENDE_SKAPAT;
import static se.sundsvall.aotloader.service.model.ItemStatus.BILAGOR_KLARA;
import static se.sundsvall.aotloader.service.model.ItemStatus.GRANSKAS;
import static se.sundsvall.aotloader.service.model.ItemStatus.KLAR;
import static se.sundsvall.aotloader.service.model.ItemStatus.MIGRERAS_INTE;
import static se.sundsvall.aotloader.service.model.ItemStatus.RELATION_KLAR;
import static se.sundsvall.aotloader.service.model.ItemStatus.TILLSTANDSHAVARE_KLAR;
import static se.sundsvall.aotloader.service.model.ItemStatus.TILLSTAND_SKAPAT;
import static se.sundsvall.aotloader.service.model.ItemStatus.VANTAR;
import static se.sundsvall.aotloader.service.model.Severity.INFO;

/**
 * Takes one item through the flow of section 6. Every step can be run again: an item continues from what the status
 * table and the target systems already have, so a rerun never creates a second errand, permit or relation (K3). An
 * error stops the item, never the run: it is saved with its step in the status table and listed in the report.
 */
@Component
public class ItemMigrator {

	private static final Logger LOG = LoggerFactory.getLogger(ItemMigrator.class);

	private final MigrationItemRepository itemRepository;
	private final ReviewExceptionRepository exceptionRepository;
	private final PartyResolver partyResolver;
	private final RelationPlanner relationPlanner;
	private final FileUploader fileUploader;
	private final TargetWriter targets;
	private final AotLoaderProperties properties;
	private final Clock clock;

	ItemMigrator(final MigrationItemRepository itemRepository, final ReviewExceptionRepository exceptionRepository, final PartyResolver partyResolver, final RelationPlanner relationPlanner,
		final FileUploader fileUploader, final TargetWriter targets, final AotLoaderProperties properties, final Clock clock) {
		this.itemRepository = itemRepository;
		this.exceptionRepository = exceptionRepository;
		this.partyResolver = partyResolver;
		this.relationPlanner = relationPlanner;
		this.fileUploader = fileUploader;
		this.targets = targets;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Takes the item as far as it can go. Never throws: whatever goes wrong is in the result, also a status table that
	 * cannot be read.
	 *
	 * @param  candidate the item
	 * @param  context   the run
	 * @return           what was done, for the report
	 */
	public ItemResult process(final Candidate candidate, final RunContext context) {
		final var key = candidate.key();
		final Optional<MigrationItemEntity> existing;
		try {
			existing = itemRepository.findBySourceAndKindAndSourceId(key.source(), key.kind(), key.sourceId());
		} catch (final RuntimeException e) {
			final var message = ErrorTexts.describe(e);
			LOG.warn("Item {} could not be read from the status table: {}", key, message);
			return new ItemResult(Outcome.FEL, List.of(), "Steg %s: statustabellen gick inte att läsa: %s".formatted(Step.LAS_OCH_BEDOM.getDisplayName(key.kind()), message), null);
		}
		if (existing.isPresent() && existing.get().getStatus() == KLAR) {
			return alreadyMigrated(candidate, context, existing.get());
		}
		return new Migration(candidate, context, existing.orElse(null)).run();
	}

	/**
	 * Leaves an item already migrated as it is: changes in the source after the migration are not carried over (decision
	 * 11). The one thing added afterwards is the relation of an inspection errand whose permit was migrated after it, for
	 * instance once the exception list let the permit through.
	 */
	private ItemResult alreadyMigrated(final Candidate candidate, final RunContext context, final MigrationItemEntity entity) {
		final var unchanged = new ItemResult(Outcome.REDAN_MIGRERAD, List.of(), null, entity.getErrandNumber());
		if (context.dryRun() || candidate.permitLink() == null || entity.getRelationId() != null || entity.getErrandId() == null) {
			return unchanged;
		}
		final var notes = new ReportNotes();
		try {
			final var plan = relationPlanner.plan(candidate, context, new ReportNotes());
			if (!plan.relates()) {
				return unchanged;
			}
			final var municipalityId = Optional.ofNullable(entity.getMunicipalityId()).orElse(context.municipalityId());
			targets.ensureRelation(municipalityId, plan.permitErrandId(), entity.getErrandId(), notes).ifPresent(relationId -> {
				entity.setRelationId(relationId);
				saveItem(entity, candidate, KLAR, context);
				notes.line("Relation till tillståndsärendet %s, tillagd i efterhand".formatted(candidate.permitLink().permitKey()));
			});
			return notes.result(Outcome.REDAN_MIGRERAD, entity.getErrandNumber());
		} catch (final RuntimeException e) {
			final var message = ErrorTexts.describe(e);
			LOG.warn("The relation of item {} could not be added: {}", candidate.key(), message);
			notes.lineFirst("Steg " + Step.RELATION.getDisplayName() + ": " + message);
			return notes.result(Outcome.FEL, entity.getErrandNumber());
		}
	}

	private MigrationItemEntity saveItem(final MigrationItemEntity entity, final Candidate candidate, final ItemStatus status, final RunContext context) {
		final var now = OffsetDateTime.now(clock);
		if (entity.getCreated() == null) {
			entity.setCreated(now);
		}
		entity.setErrandType(candidate.type());
		entity.setPlaceName(Texts.truncate(candidate.placeName(), PLACE_NAME_MAX_LENGTH, null));
		entity.setPlaceNumber(Texts.truncate(candidate.placeNumber(), PLACE_NUMBER_MAX_LENGTH, null));
		entity.setErrandNumber(Texts.truncate(entity.getErrandNumber(), ERRAND_NUMBER_MAX_LENGTH, null));
		entity.setStatus(status);
		if (status != ItemStatus.FEL) {
			entity.setFailedStep(null);
		}
		if (status.isProgress()) {
			entity.setReason(null);
		}
		entity.setLastRunId(context.runId());
		entity.setModified(now);
		return itemRepository.save(entity);
	}

	private static MigrationItemEntity newEntity(final Candidate candidate) {
		final var entity = new MigrationItemEntity();
		entity.setSource(candidate.key().source());
		entity.setKind(candidate.key().kind());
		entity.setSourceId(candidate.key().sourceId());
		return entity;
	}

	/**
	 * The migration of one item that is not migrated yet. It keeps the row of the status table and the step the item is
	 * in, so that an error is saved with the step it stopped in.
	 */
	private final class Migration {

		private final Candidate candidate;
		private final RunContext context;
		private final ReportNotes notes = new ReportNotes();
		private MigrationItemEntity entity;
		private Step step = Step.LAS_OCH_BEDOM;

		private Migration(final Candidate candidate, final RunContext context, final MigrationItemEntity entity) {
			this.candidate = candidate;
			this.context = context;
			this.entity = entity;
		}

		private ItemResult run() {
			try {
				return migrate();
			} catch (final RuntimeException e) {
				return fail(e);
			}
		}

		private ItemResult migrate() {
			final var municipalityId = municipalityOf();
			final var hits = new ArrayList<>(candidate.ruleHits());
			step = Step.TILLSTANDSHAVARE;
			final var parties = partyResolver.resolve(candidate, municipalityId, context, hits, notes);
			step = Step.LAS_OCH_BEDOM;
			final var assessment = assess(hits);
			if (assessment.decision() == Decision.REVIEW || assessment.decision() == Decision.NOT_MIGRATED) {
				if (wasActivated(municipalityId)) {
					return activatedBefore();
				}
				return assessment.decision() == Decision.REVIEW
					? stop(GRANSKAS, Outcome.GRANSKAS, reportTexts(assessment.reasons()))
					: stop(MIGRERAS_INTE, Outcome.MIGRERAS_INTE, reportTexts(assessment.reasons()));
			}
			final var relationPlan = relationPlanner.plan(candidate, context, notes);
			if (relationPlan.waits()) {
				return wasActivated(municipalityId) ? activatedBefore() : stop(VANTAR, Outcome.VANTAR, List.of(relationPlan.waitReason()));
			}
			if (context.dryRun()) {
				fileUploader.check(candidate, context, notes);
				noteTruncatedParameters(fileUploader.withLegalBasis(candidate, context, notes));
				return notes.result(Outcome.SKULLE_MIGRERAS, errandNumber());
			}
			if (!context.tryCountWrite()) {
				return new ItemResult(Outcome.EJ_KORD, List.of(), "Körningens gräns för antal ärenden nåddes", null);
			}
			write(municipalityId, parties, assessment.decision() == Decision.MIGRATE_AS_EXPIRED, relationPlan);
			return notes.result(Outcome.MIGRERAD, entity.getErrandNumber());
		}

		/**
		 * The steps that write to the target systems, each saved in the status table when it is done.
		 */
		private void write(final String municipalityId, final PartyIds parties, final boolean expired, final RelationPlan relationPlan) {
			ensureEntity();
			// Kept from the first write on, so that a later run never looks for the item in another municipality
			entity.setMunicipalityId(municipalityId);
			saveAs(TILLSTANDSHAVARE_KLAR);

			step = Step.ARENDE;
			final var earlierErrandId = entity.getErrandId();
			final var errand = targets.ensureErrand(candidate, municipalityId, entity.getErrandId(), parties, context.labelIds(municipalityId));
			if (entity.getErrandId() != null && !entity.getErrandId().equals(errand.getId())) {
				forgetGoneErrand();
			}
			entity.setErrandId(errand.getId());
			entity.setErrandNumber(errand.getErrandNumber());
			saveAs(ARENDE_SKAPAT);
			final var active = errand.getLifecycle() == LifecycleEnum.ACTIVE;
			if (!active) {
				targets.ensureInvestigation(candidate, municipalityId, errand.getId());
			}

			step = Step.BILAGOR;
			if (!active) {
				final var material = fileUploader.upload(candidate, context, municipalityId, errand.getId(), notes);
				fileUploader.linkToInvestigation(candidate, municipalityId, errand.getId(), material, notes);
			}
			saveAs(BILAGOR_KLARA);

			step = Step.TILLSTAND;
			if (candidate.asset() != null) {
				if (active) {
					entity.setPartyAssetsId(targets.findMigratedAsset(candidate, municipalityId, errand.getId(), entity.getPartyAssetsId(), parties, notes));
					entity.setAssetId(errand.getId());
				} else {
					writeAsset(municipalityId, errand.getId(), parties, expired, errand.getId().equals(earlierErrandId));
				}
				saveAs(TILLSTAND_SKAPAT);
			}

			step = Step.RELATION;
			if (relationPlan.relates()) {
				if (entity.getRelationId() == null) {
					targets.ensureRelation(municipalityId, relationPlan.permitErrandId(), errand.getId(), notes).ifPresent(entity::setRelationId);
				}
				saveAs(RELATION_KLAR);
			}

			step = Step.AKTIVERA;
			if (!active) {
				targets.activate(municipalityId, errand.getId());
			}
			saveAs(KLAR);
		}

		/**
		 * Tells in the report which parameters of the post PartyAssets takes only cut to 255 characters, since a certificate
		 * the new system renders from them would show them cut.
		 *
		 * @param withLegalBasis the candidate with the legal basis read from its documents, which is one of the parameters
		 */
		private void noteTruncatedParameters(final Candidate withLegalBasis) {
			Optional.ofNullable(withLegalBasis.asset()).map(TargetMapper::truncatedParameters).filter(keys -> !keys.isEmpty())
				.ifPresent(keys -> notes.line("Parametrar som kortas till 255 tecken i PartyAssets: " + String.join(", ", keys)));
		}

		/**
		 * Makes the post in PartyAssets of an item whose errand is a draft, or brings the one an earlier run made up to date,
		 * with its legal basis and its files. A post that is to be EXPIRED is ACTIVE while it gets its content and the files it
		 * lacks, since an EXPIRED post takes neither, and EXPIRED once at the end.
		 */
		private void writeAsset(final String municipalityId, final String errandId, final PartyIds parties, final boolean expired, final boolean earlierErrand) {
			final var withLegalBasis = fileUploader.withLegalBasis(candidate, context, notes);
			noteTruncatedParameters(withLegalBasis);
			final var post = targets.ensureAsset(withLegalBasis, municipalityId, new PostLookup(errandId, entity.getPartyAssetsId(), earlierErrand), parties, expired, notes);
			final var partyAssetsId = post.id();
			entity.setPartyAssetsId(partyAssetsId);
			entity.setAssetId(errandId);
			if (!expired) {
				fileUploader.uploadToAsset(candidate, context, municipalityId, partyAssetsId, notes);
				return;
			}
			// An EXPIRED post is ACTIVE while it gets its files, and EXPIRED once at the end
			if (post.status() == Status.EXPIRED) {
				if (fileUploader.missingOnAsset(candidate, context, municipalityId, partyAssetsId) == 0) {
					return;
				}
				targets.changeAssetStatus(municipalityId, partyAssetsId, Status.ACTIVE, words(), notes);
			}
			try {
				fileUploader.uploadToAsset(candidate, context, municipalityId, partyAssetsId, notes);
			} catch (final RuntimeException e) {
				restoreExpired(municipalityId, partyAssetsId, e);
				throw e;
			}
			targets.changeAssetStatus(municipalityId, partyAssetsId, Status.EXPIRED, words(), notes);
		}

		/**
		 * The municipality the item is written to: the one the run is started for, also for a place in an area of another
		 * municipality, which only gets that municipality as a parameter (decision 19). An item already written to another
		 * municipality stays there; were it moved, a second errand would be created.
		 */
		private String municipalityOf() {
			final var municipalityId = context.municipalityId();
			if (entity != null && entity.getMunicipalityId() != null && !entity.getMunicipalityId().equals(municipalityId)) {
				throw new MigrationException("Ärendet migrerades till kommun %s, men körningen gäller kommun %s. Det flyttas inte, eftersom det då skulle skapas på nytt."
					.formatted(entity.getMunicipalityId(), municipalityId));
			}
			final var premisesMunicipalityId = properties.premisesMunicipalityOf(candidate.place(), municipalityId);
			if (!premisesMunicipalityId.equals(municipalityId)) {
				notes.line("Stället hör till kommun %s (område %s)".formatted(premisesMunicipalityId, candidate.place().area()));
			}
			return municipalityId;
		}

		/**
		 * Assesses the hits against the exception list, and notes the hits and what the exception list said.
		 */
		private Assessment assess(final List<RuleHit> hits) {
			final var key = candidate.key();
			final var exception = exceptionRepository.findBySourceAndKindAndSourceId(key.source(), key.kind(), key.sourceId()).map(ReviewExceptionEntity::getOutcome);
			final var assessment = Assessment.of(hits, exception);
			hits.forEach(hit -> notes.rule(hit.rule().getCode()));
			hits.stream().filter(hit -> hit.rule().getSeverity() == INFO).forEach(hit -> notes.line(hit.toReportText()));
			Optional.ofNullable(assessment.note()).ifPresent(notes::line);
			return assessment;
		}

		/**
		 * The errand in the status table is gone and another took its place. The permit and the relation pointed to the old
		 * errand, so they are made again for the new one; the old ones are listed for the reset (section 10.3) to remove.
		 */
		private void forgetGoneErrand() {
			notes.line("Ärendet %s fanns inte längre i SupportManagement och ersattes".formatted(entity.getErrandId()));
			Optional.ofNullable(entity.getPartyAssetsId()).ifPresent(id -> notes.line("%s %s i PartyAssets hör till det gamla ärendet och ska tas bort".formatted(words().capitalized(), id)));
			Optional.ofNullable(entity.getRelationId()).ifPresent(id -> notes.line("Relationen %s hör till det gamla ärendet och ska tas bort".formatted(id)));
			entity.setAssetId(null);
			entity.setPartyAssetsId(null);
			entity.setRelationId(null);
		}

		/**
		 * @return true when an earlier run activated the errand of the item, although the status table does not say so
		 */
		private boolean wasActivated(final String municipalityId) {
			return entity != null && entity.getErrandId() != null && targets.isActive(municipalityId, entity.getErrandId());
		}

		/**
		 * Counts an item whose errand an earlier run activated as migrated, rather than stopping it as a draft: activating is
		 * the last step, so everything before it was done, and changes in the source since are not carried over (decision 11).
		 * A relation that waits for its permit is added by a later run.
		 */
		private ItemResult activatedBefore() {
			notes.lineFirst("Ärendet %s är redan aktivt i SupportManagement och räknas som migrerat. Ändringar i källan efter det förs inte över (beslut 11)"
				.formatted(Optional.ofNullable(entity.getErrandNumber()).orElse(entity.getErrandId())));
			if (!context.dryRun()) {
				saveAs(KLAR);
			}
			return notes.result(Outcome.REDAN_MIGRERAD, errandNumber());
		}

		/**
		 * Stops the item before it is written, with the reasons first in the report.
		 */
		private ItemResult stop(final ItemStatus status, final Outcome outcome, final List<String> stopReasons) {
			final var reasons = new ArrayList<>(stopReasons);
			earlierWrites().ifPresent(reasons::add);
			reasons.reversed().forEach(notes::lineFirst);
			if (!context.dryRun()) {
				ensureEntity();
				entity.setReason(ReportNotes.reportText(String.join("; ", reasons)));
				saveAs(status);
			}
			return notes.result(outcome, errandNumber());
		}

		private AssetWords words() {
			return AssetWords.of(candidate.key().kind());
		}

		/**
		 * Gives the post in PartyAssets EXPIRED back after its files failed, so that it does not show as valid until the next
		 * run. When that fails too, the report says the post is left ACTIVE.
		 */
		private void restoreExpired(final String municipalityId, final String partyAssetsId, final RuntimeException failure) {
			try {
				targets.changeAssetStatus(municipalityId, partyAssetsId, Status.EXPIRED, words(), notes);
			} catch (final RuntimeException e) {
				failure.addSuppressed(e);
				notes.line("%s i PartyAssets är ACTIVE, fast %s ska vara EXPIRED, tills nästa körning ger %s sina filer".formatted(words().capitalized(), words().pronoun(), words().pronoun()));
			}
		}

		/**
		 * @return what an earlier run of the item wrote to the target systems, which stays there while the item is stopped
		 */
		private Optional<String> earlierWrites() {
			if (entity == null || entity.getErrandId() == null) {
				return Optional.empty();
			}
			return Optional.of("Ärendet %s från en tidigare körning finns kvar i SupportManagement%s. Det slutförs om ärendet får migreras, annars hanteras det enligt avsnitt 10.3"
				.formatted(Optional.ofNullable(entity.getErrandNumber()).orElse(entity.getErrandId()), entity.getPartyAssetsId() == null ? "" : ", och %s i PartyAssets".formatted(words().definite())));
		}

		private ItemResult fail(final RuntimeException e) {
			final var message = ErrorTexts.describe(e);
			LOG.warn("Item {} failed in step {}: {}", candidate.key(), step, message);
			if (!context.dryRun()) {
				saveFailure(message);
			}
			notes.lineFirst("Steg " + step.getDisplayName(candidate.key().kind()) + ": " + message);
			return notes.result(Outcome.FEL, errandNumber());
		}

		private void saveFailure(final String message) {
			try {
				ensureEntity();
				entity.setFailedStep(step);
				entity.setReason(ReportNotes.reportText(message));
				saveAs(ItemStatus.FEL);
			} catch (final RuntimeException e) {
				LOG.error("The failure of item {} could not be saved in the status table: {}", candidate.key(), e.getClass().getSimpleName());
			}
		}

		/**
		 * Gives the item a row of the status table, unless an earlier run did.
		 */
		private void ensureEntity() {
			if (entity == null) {
				entity = newEntity(candidate);
			}
		}

		private void saveAs(final ItemStatus status) {
			entity = saveItem(entity, candidate, status, context);
		}

		private String errandNumber() {
			return entity == null ? null : entity.getErrandNumber();
		}

		private static List<String> reportTexts(final List<RuleHit> hits) {
			return hits.stream().map(RuleHit::toReportText).toList();
		}
	}
}
