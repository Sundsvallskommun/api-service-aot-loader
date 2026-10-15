package se.sundsvall.aotloader.service;

import com.google.common.hash.Hashing;
import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetAttachment;
import generated.se.sundsvall.partyassets.Status;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.partyassets.PartyAssetsIntegration;
import se.sundsvall.aotloader.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.aotloader.service.model.AssetFile;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.service.model.FilePurpose;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.source.files.FileArea.LookupResult;
import se.sundsvall.aotloader.support.MigrationException;

import static java.util.stream.Collectors.joining;
import static se.sundsvall.aotloader.configuration.ClockConfiguration.SWEDISH_TIME;
import static se.sundsvall.aotloader.service.model.ReviewRule.FILE_NOT_UPLOADED;

/**
 * Uploads the files of an item to its draft errand, and those that belong on its post in PartyAssets there, or in a dry
 * run checks that they can be found (steps 3 and 4 of section 6). The file names of the files that are not uploaded are
 * listed in the report, for the business to find the files (G14).
 */
@Component
class FileUploader {

	static final int MAX_FILE_NAMES_IN_REPORT = 20;
	private static final String NO_CATEGORY = "utan kategori";

	private final SupportManagementIntegration supportManagement;
	private final PartyAssetsIntegration partyAssets;
	private final AotLoaderProperties properties;
	private final Clock clock;

	FileUploader(final SupportManagementIntegration supportManagement, final PartyAssetsIntegration partyAssets, final AotLoaderProperties properties, final Clock clock) {
		this.supportManagement = supportManagement;
		this.partyAssets = partyAssets;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Uploads the files to the draft, each with its purpose and the date it came in (decision 40). A file already on the
	 * errand, with the same name and content, is not uploaded again, but gets the purpose and date it lacks. A file that
	 * cannot be uploaded for good is listed in the report.
	 *
	 * @throws MigrationException when the file area cannot be read, or a purpose of a file was not read for the
	 *                            municipality, so that the step is made again in the next run
	 */
	void upload(final Candidate candidate, final RunContext context, final String municipalityId, final String errandId, final ReportNotes notes) {
		if (candidate.files().isEmpty()) {
			return;
		}
		final var fileArea = context.fileArea(candidate.key().source());
		final var attached = new ArrayList<>(supportManagement.getAttachments(municipalityId, errandId).stream().map(AttachedFile::of).toList());
		final var problems = new FileProblems();
		for (final var reference : candidate.files()) {
			final var lookup = readableLookup(fileArea, reference, candidate);
			problemOf(reference, lookup).ifPresentOrElse(
				problem -> problems.add(problem, lookup.name()),
				() -> uploadFile(municipalityId, errandId, lookup, wanted(candidate.detailsOf(reference), context, municipalityId), attached, problems));
		}
		problems.report(notes, maxFileSizeText());
	}

	/**
	 * Checks that the files can be found and would be uploaded, without uploading anything, and tells which would go on
	 * the post in PartyAssets.
	 */
	void check(final Candidate candidate, final RunContext context, final ReportNotes notes) {
		if (candidate.files().isEmpty()) {
			return;
		}
		final var fileArea = context.fileArea(candidate.key().source());
		final var problems = new FileProblems();
		for (final var reference : candidate.files()) {
			final var lookup = fileArea.find(reference);
			problemOf(reference, lookup).ifPresent(problem -> problems.add(problem, lookup.name()));
		}
		if (candidate.asset() != null && !candidate.assetFiles().isEmpty()) {
			notes.line("Till tillståndet i PartyAssets: " + categoryCounts(candidate, context, context.municipalityId()));
		}
		problems.report(notes, maxFileSizeText());
	}

	/**
	 * Puts the files that belong on the post in PartyAssets there, each in its category if it has one (see
	 * {@link #categoryOf}). A file already on
	 * the post, with the same name and size, is not put there again. A file that the errand does not get either, since it
	 * cannot be found or is not to be uploaded, is skipped without a note: every such file is one of the errand's, whose
	 * step lists it (G14). A file PartyAssets refuses is listed.
	 *
	 * @throws MigrationException when the file area cannot be read, or when PartyAssets refuses a file because the post is
	 *                            not ACTIVE, so that the step is made again in the next run
	 */
	void uploadToAsset(final Candidate candidate, final RunContext context, final String municipalityId, final String partyAssetsId, final ReportNotes notes) {
		if (candidate.assetFiles().isEmpty()) {
			return;
		}
		final var fileArea = context.fileArea(candidate.key().source());
		final var attached = new ArrayList<>(partyAssets.getAttachments(municipalityId, partyAssetsId).stream().map(AttachedFile::of).toList());
		final var problems = new FileProblems();
		var onPost = 0;
		for (final var file : candidate.assetFiles()) {
			final var lookup = readableLookup(fileArea, file.reference(), candidate);
			if (problemOf(file.reference(), lookup).isPresent()) {
				continue;
			}
			if (uploadToAsset(municipalityId, partyAssetsId, lookup, categoryOf(candidate, file, context, municipalityId), attached, problems)) {
				onPost++;
			}
		}
		notes.line("Tillståndet i PartyAssets har %d av %d filer: %s".formatted(onPost, candidate.assetFiles().size(), categoryCounts(candidate, context, municipalityId)));
		problems.report(notes, maxFileSizeText());
	}

	/**
	 * @return true when the file is on the post, put there now or by an earlier run
	 */
	private boolean uploadToAsset(final String municipalityId, final String partyAssetsId, final FileArea.Lookup lookup, final String category, final List<AttachedFile> attached,
		final FileProblems problems) {
		final byte[] content;
		try {
			content = FileArea.read(lookup.path());
		} catch (final IOException e) {
			problems.add(FileProblem.UNREADABLE, lookup.name());
			return false;
		}
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
			throw new MigrationException("Tillståndet %s i PartyAssets har status %s och tar inte emot filer".formatted(partyAssetsId, status));
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
		if (candidate.assetFiles().isEmpty()) {
			return 0;
		}
		final var fileArea = context.fileArea(candidate.key().source());
		final var attached = partyAssets.getAttachments(municipalityId, partyAssetsId).stream().map(AttachedFile::of).toList();
		var missing = 0;
		for (final var file : candidate.assetFiles()) {
			final var lookup = readableLookup(fileArea, file.reference(), candidate);
			final var onFileArea = new AttachedFile(lookup.name(), null, (int) lookup.size());
			if (problemOf(file.reference(), lookup).isEmpty() && attached.stream().noneMatch(onFileArea::isSameAs)) {
				missing++;
			}
		}
		return missing;
	}

	/**
	 * Looks a file up in the file area of the candidate's source.
	 *
	 * @throws MigrationException when the file area cannot be read
	 */
	private static FileArea.Lookup readableLookup(final FileArea fileArea, final FileReference reference, final Candidate candidate) {
		final var lookup = fileArea.find(reference);
		if (lookup.result() == LookupResult.DIRECTORY_UNAVAILABLE) {
			throw new MigrationException("Filarean för %s gick inte att läsa".formatted(candidate.key().source().getDisplayName()));
		}
		return lookup;
	}

	/**
	 * @return the category of the file on the post: the name shown of its purpose when it has one, as pw-alkt gives it, and
	 *         otherwise the category the item gives it
	 */
	private static String categoryOf(final Candidate candidate, final AssetFile file, final RunContext context, final String municipalityId) {
		return Optional.ofNullable(candidate.detailsOf(file.reference()).purpose())
			.flatMap(name -> context.purpose(municipalityId, name))
			.map(FilePurpose::displayName)
			.orElse(file.category());
	}

	/**
	 * @return the number of files for the post per category, e.g. "Tillståndsbevis 1, Ritning 2", with those without a
	 *         category as "utan kategori"
	 */
	private static String categoryCounts(final Candidate candidate, final RunContext context, final String municipalityId) {
		final var counts = new LinkedHashMap<String, Integer>();
		candidate.assetFiles().forEach(file -> counts.merge(Optional.ofNullable(categoryOf(candidate, file, context, municipalityId)).orElse(NO_CATEGORY), 1, Integer::sum));
		return counts.entrySet().stream().map(entry -> entry.getKey() + " " + entry.getValue()).collect(joining(", "));
	}

	/**
	 * Why a file is not uploaded. A link or an invalid name is that whatever its name; any other file is not uploaded when
	 * another place refers to a file with the same name, since the file may belong to that place.
	 *
	 * @return the problem, or empty when the file is found and is to be uploaded
	 */
	private static Optional<FileProblem> problemOf(final FileReference reference, final FileArea.Lookup lookup) {
		return switch (lookup.result()) {
			case LINK, INVALID_NAME, DIRECTORY_UNAVAILABLE -> Optional.of(FileProblem.of(lookup.result()));
			case FOUND -> reference.sharedWithAnotherPlace() ? Optional.of(FileProblem.SHARED_NAME) : Optional.empty();
			default -> Optional.of(reference.sharedWithAnotherPlace() ? FileProblem.SHARED_NAME : FileProblem.of(lookup.result()));
		};
	}

	private void uploadFile(final String municipalityId, final String errandId, final FileArea.Lookup lookup, final Wanted wanted, final List<AttachedFile> attached,
		final FileProblems problems) {
		final byte[] content;
		try {
			content = FileArea.read(lookup.path());
		} catch (final IOException e) {
			problems.add(FileProblem.UNREADABLE, lookup.name());
			return;
		}
		final var file = new AttachedFile(lookup.name(), sha256(content), content.length);
		final var existing = attached.stream().filter(file::isSameAs).findFirst();
		if (existing.isPresent()) {
			completeAttachment(municipalityId, errandId, existing.get(), wanted);
			return;
		}
		supportManagement.createAttachment(municipalityId, errandId, lookup.name(), content, wanted.received()).ifPresentOrElse(id -> {
			Optional.ofNullable(wanted.purposeId()).ifPresent(purposeId -> supportManagement.updateAttachment(municipalityId, errandId, id, purposeId, null));
			attached.add(new AttachedFile(file.name(), file.hash(), file.size(), id, wanted.purposeId(), wanted.received()));
		}, () -> problems.add(FileProblem.REJECTED, lookup.name()));
	}

	/**
	 * Gives a file already on the errand, from an earlier run, the purpose and date it lacks. A purpose the file has is
	 * left as it is when the file is to have none.
	 */
	private void completeAttachment(final String municipalityId, final String errandId, final AttachedFile existing, final Wanted wanted) {
		if (existing.id() == null) {
			return;
		}
		final var purposeId = Objects.equals(wanted.purposeId(), existing.purposeId()) ? null : wanted.purposeId();
		final var received = wanted.received() == null || (existing.received() != null && existing.received().isEqual(wanted.received())) ? null : wanted.received();
		if (purposeId != null || received != null) {
			supportManagement.updateAttachment(municipalityId, errandId, existing.id(), purposeId, received);
		}
	}

	/**
	 * @return                    the purpose and date the attachment of the file is to have in SupportManagement
	 * @throws MigrationException when the purpose of the file was not read for the municipality
	 */
	private Wanted wanted(final FileDetails details, final RunContext context, final String municipalityId) {
		final var purposeId = Optional.ofNullable(details.purpose())
			.map(name -> context.purpose(municipalityId, name)
				.map(FilePurpose::id)
				.orElseThrow(() -> new MigrationException("Syftet %s för bilagor är inte läst för kommun %s".formatted(name, municipalityId))))
			.orElse(null);
		// SupportManagement refuses a time later than now, which a source may have by mistake
		final var received = Optional.ofNullable(details.received())
			.map(time -> time.atZone(SWEDISH_TIME).toOffsetDateTime())
			.filter(time -> !time.isAfter(OffsetDateTime.now(clock)))
			.orElse(null);
		return new Wanted(purposeId, received);
	}

	/**
	 * What the attachment of a file is to have.
	 *
	 * @param purposeId the id of its attachment purpose, null for none
	 * @param received  when the file came in, null for the time of upload
	 */
	private record Wanted(String purposeId, OffsetDateTime received) {
	}

	private String maxFileSizeText() {
		return properties.maxFileSize().toMegabytes() + " MB";
	}

	/**
	 * @return the SHA-256 hash of the content, hex encoded in lower case, as SupportManagement gives it each attachment
	 */
	static String sha256(final byte[] content) {
		return Hashing.sha256().hashBytes(content).toString();
	}

	/**
	 * A file on the errand, or about to be uploaded to it.
	 *
	 * @param name      the file name
	 * @param hash      the SHA-256 hash of the content, null when SupportManagement did not give it
	 * @param size      the size in bytes, null when SupportManagement did not give it
	 * @param id        the id of the attachment, null for a file not uploaded yet
	 * @param purposeId the id of the attachment purpose, null for none
	 * @param received  when the file came in, null when unknown
	 */
	record AttachedFile(String name, String hash, Integer size, String id, String purposeId, OffsetDateTime received) {

		AttachedFile(final String name, final String hash, final Integer size) {
			this(name, hash, size, null, null, null);
		}

		static AttachedFile of(final ErrandAttachment attachment) {
			return new AttachedFile(attachment.getFileName(), attachment.getHash(), attachment.getFileSize(), attachment.getId(),
				Optional.ofNullable(attachment.getPurpose()).map(ErrandAttachmentPurpose::getId).orElse(null), attachment.getReceived());
		}

		static AttachedFile of(final AssetAttachment attachment) {
			return new AttachedFile(attachment.getFileName(), null, attachment.getFileSize());
		}

		/**
		 * @return true for the same name, regardless of case, and the same content: the same hash, or the same size when a
		 *         hash is missing. Without either, the files are taken to differ, since a file uploaded twice is better than
		 *         one that is never uploaded.
		 */
		boolean isSameAs(final AttachedFile other) {
			if (name == null || !name.equalsIgnoreCase(other.name())) {
				return false;
			}
			if (hash != null && other.hash() != null) {
				return hash.equalsIgnoreCase(other.hash());
			}
			return size != null && Objects.equals(size, other.size());
		}
	}

	/**
	 * Why a file was not uploaded, with the text of the report for one file and for several.
	 */
	enum FileProblem {

		MISSING("fil saknas på filarean", "filer saknas på filarean"),
		TOO_LARGE("fil är större än %s och laddas inte upp", "filer är större än %s och laddas inte upp"),
		LINK("länk hoppas över", "länkar hoppas över"),
		INVALID_NAME("filreferens har ett ogiltigt namn", "filreferenser har ett ogiltigt namn"),
		UNREADABLE("fil gick inte att läsa", "filer gick inte att läsa"),
		DIRECTORY_UNAVAILABLE("fil kunde inte kontrolleras, filarean gick inte att läsa", "filer kunde inte kontrolleras, filarean gick inte att läsa"),
		REJECTED("fil togs inte emot av SupportManagement", "filer togs inte emot av SupportManagement"),
		REFUSED_BY_PARTY_ASSETS("fil togs inte emot av PartyAssets och ligger bara på ärendet", "filer togs inte emot av PartyAssets och ligger bara på ärendet"),
		SHARED_NAME("fil har ett namn som också används på ett annat ställe och laddas inte upp", "filer har namn som också används på andra ställen och laddas inte upp");

		private final String one;
		private final String several;

		FileProblem(final String one, final String several) {
			this.one = one;
			this.several = several;
		}

		static FileProblem of(final LookupResult result) {
			return switch (result) {
				case MISSING -> MISSING;
				case TOO_LARGE -> TOO_LARGE;
				case LINK -> LINK;
				case INVALID_NAME -> INVALID_NAME;
				case UNREADABLE -> UNREADABLE;
				case DIRECTORY_UNAVAILABLE -> DIRECTORY_UNAVAILABLE;
				case FOUND -> throw new IllegalArgumentException("A file that is found is not a problem");
			};
		}

		/**
		 * @return false for a link, which is skipped on purpose (section 6) and is no hit on G14
		 */
		boolean isRuleHit() {
			return this != LINK;
		}

		String text(final int count, final String maxFileSize) {
			return (count == 1 ? one : several).formatted(maxFileSize);
		}
	}

	/**
	 * The files that were not uploaded, by problem. A name may hold a person's name; the report masks numbers only.
	 */
	static final class FileProblems {

		private final Map<FileProblem, List<String>> names = new EnumMap<>(FileProblem.class);

		void add(final FileProblem problem, final String name) {
			names.computeIfAbsent(problem, _ -> new ArrayList<>()).add(name);
		}

		void report(final ReportNotes notes, final String maxFileSize) {
			if (names.keySet().stream().anyMatch(FileProblem::isRuleHit)) {
				notes.rule(FILE_NOT_UPLOADED.getCode());
			}
			names.forEach((problem, files) -> notes.line(
				(problem.isRuleHit() ? FILE_NOT_UPLOADED.getCode() + " " : "") + files.size() + " " + problem.text(files.size(), maxFileSize) + " (" + list(files) + ")"));
		}

		private static String list(final List<String> files) {
			final var shown = files.stream().limit(MAX_FILE_NAMES_IN_REPORT).toList();
			final var rest = files.size() - shown.size();
			return String.join(", ", shown) + (rest > 0 ? " och %d till".formatted(rest) : "");
		}
	}
}
