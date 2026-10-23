package se.sundsvall.aotloader.service;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.Status;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.aotloader.service.model.AssetFile;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.FilePurpose;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.support.MigrationException;

import static java.util.stream.Collectors.joining;

/**
 * Puts the files of an item that belong on its post in PartyAssets there (step 4 of section 6).
 */
@Component
class AssetFileUploader {

	private static final String NO_CATEGORY = "utan kategori";

	private final PartyAssetsIntegration partyAssets;
	private final ItemFiles itemFiles;

	AssetFileUploader(final PartyAssetsIntegration partyAssets, final AotLoaderProperties properties) {
		this.partyAssets = partyAssets;
		this.itemFiles = new ItemFiles(properties);
	}

	/**
	 * Puts the files that belong on the post in PartyAssets there, each in its category if it has one (see
	 * {@link #categoryOf}). A file already on the post, with the same name and size, is not put there again. A file that
	 * the errand does not get either, since it cannot be found or is not to be uploaded, is skipped without a note: every
	 * such file is one of the errand's, whose step lists it (G14). A file PartyAssets refuses is listed.
	 *
	 * @throws MigrationException when the file area or a file cannot be read, or when PartyAssets refuses a file because
	 *                            the post is not ACTIVE, so that the step is made again in the next run
	 */
	void uploadToAsset(final Candidate candidate, final RunContext context, final String municipalityId, final String partyAssetsId, final ReportNotes notes) {
		if (candidate.files().onPost().isEmpty()) {
			return;
		}
		final var fileArea = context.fileArea(candidate.key().source());
		final var attached = new ArrayList<>(partyAssets.getAttachments(municipalityId, partyAssetsId).stream().map(AttachedFile::of).toList());
		final var problems = new FileProblems();
		final var words = AssetWords.of(candidate.key().kind());
		var onPost = 0;
		for (final var file : candidate.files().onPost()) {
			final var lookup = ItemFiles.find(fileArea, file.reference(), candidate);
			final var problem = FileProblem.of(file.reference(), lookup);
			if (problem.isPresent()) {
				// Also the errand's step lists it, but one that cannot be read may be readable in the next run
				problem.filter(FileProblem.UNREADABLE::equals).ifPresent(unreadable -> problems.add(unreadable, lookup.name()));
				continue;
			}
			if (uploadToAsset(municipalityId, partyAssetsId, lookup, categoryOf(candidate, file, context, municipalityId), attached, words, problems)) {
				onPost++;
			}
		}
		notes.line("%s i PartyAssets har %d av %d filer: %s".formatted(words.capitalized(), onPost, candidate.files().onPost().size(), categoryCounts(candidate, context, municipalityId)));
		itemFiles.reportAndFailOnUnreadable(problems, notes, candidate);
	}

	/**
	 * @return true when the file is on the post, put there now or by an earlier run
	 */
	private boolean uploadToAsset(final String municipalityId, final String partyAssetsId, final FileArea.Lookup lookup, final String category, final List<AttachedFile> attached,
		final AssetWords words, final FileProblems problems) {
		final var read = itemFiles.read(lookup, problems);
		if (read.isEmpty()) {
			return false;
		}
		final var content = read.get();
		// PartyAssets gives no hash, so the size tells the content apart
		final var file = new AttachedFile(lookup.name(), null, content.length);
		if (attached.stream().anyMatch(file::isSameAs)) {
			return true;
		}
		if (partyAssets.createAttachment(municipalityId, partyAssetsId, lookup.name(), content, category)) {
			attached.add(file);
			return true;
		}
		// PartyAssets answers the same when the post takes no files, as after the nightly job has made it EXPIRED
		final var status = partyAssets.getAsset(municipalityId, partyAssetsId).map(Asset::getStatus).orElse(null);
		if (status != Status.ACTIVE) {
			throw new MigrationException("%s %s i PartyAssets har status %s och tar inte emot filer".formatted(words.capitalized(), partyAssetsId, status));
		}
		problems.add(FileProblem.REFUSED_BY_PARTY_ASSETS, lookup.name());
		return false;
	}

	/**
	 * @return                    the number of files for the post in PartyAssets that are to be put there and are not on it
	 *                            yet
	 * @throws MigrationException when the file area cannot be read
	 */
	int missingOnAsset(final Candidate candidate, final RunContext context, final String municipalityId, final String partyAssetsId) {
		if (candidate.files().onPost().isEmpty()) {
			return 0;
		}
		final var fileArea = context.fileArea(candidate.key().source());
		final var attached = partyAssets.getAttachments(municipalityId, partyAssetsId).stream().map(AttachedFile::of).toList();
		var missing = 0;
		for (final var file : candidate.files().onPost()) {
			final var lookup = ItemFiles.find(fileArea, file.reference(), candidate);
			final var onFileArea = new AttachedFile(lookup.name(), null, (int) lookup.size());
			if (FileProblem.of(file.reference(), lookup).isEmpty() && attached.stream().noneMatch(onFileArea::isSameAs)) {
				missing++;
			}
		}
		return missing;
	}

	/**
	 * @return the category of the file on the post: the name shown of its purpose when it has one, as pw-alkt gives it, and
	 *         otherwise the category the item gives it
	 */
	private static String categoryOf(final Candidate candidate, final AssetFile file, final RunContext context, final String municipalityId) {
		return Optional.ofNullable(candidate.files().detailsOf(file.reference()).purpose())
			.flatMap(name -> context.purpose(municipalityId, name))
			.map(FilePurpose::displayName)
			.orElse(file.category());
	}

	/**
	 * @return the number of files for the post per category, e.g. "Tillståndsbevis 1, Ritning 2", with those without a
	 *         category as "utan kategori"
	 */
	static String categoryCounts(final Candidate candidate, final RunContext context, final String municipalityId) {
		final var counts = new LinkedHashMap<String, Integer>();
		candidate.files().onPost().forEach(file -> counts.merge(Optional.ofNullable(categoryOf(candidate, file, context, municipalityId)).orElse(NO_CATEGORY), 1, Integer::sum));
		return counts.entrySet().stream().map(entry -> entry.getKey() + " " + entry.getValue()).collect(joining(", "));
	}
}
