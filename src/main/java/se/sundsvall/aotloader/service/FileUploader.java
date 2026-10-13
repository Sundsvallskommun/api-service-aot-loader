package se.sundsvall.aotloader.service;

import com.google.common.hash.Hashing;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.source.files.FileArea.LookupResult;
import se.sundsvall.aotloader.support.MigrationException;

import static se.sundsvall.aotloader.service.model.ReviewRule.FILE_NOT_UPLOADED;

/**
 * Uploads the files of an item to its draft errand, or in a dry run checks that they can be found (step 3 of section
 * 6). The file names of the files that are not uploaded are listed in the report, for the business to find the files
 * (G14).
 */
@Component
class FileUploader {

	static final int MAX_FILE_NAMES_IN_REPORT = 20;

	private final SupportManagementIntegration supportManagement;
	private final AotLoaderProperties properties;

	FileUploader(final SupportManagementIntegration supportManagement, final AotLoaderProperties properties) {
		this.supportManagement = supportManagement;
		this.properties = properties;
	}

	/**
	 * Uploads the files to the draft. A file already on the errand, with the same name and content, is not uploaded again.
	 * A file that cannot be uploaded for good is listed in the report.
	 *
	 * @throws MigrationException when the file area cannot be read, so that the step is made again in the next run
	 */
	void upload(final Candidate candidate, final RunContext context, final String municipalityId, final String errandId, final ReportNotes notes) {
		if (candidate.files().isEmpty()) {
			return;
		}
		final var fileArea = context.fileArea(candidate.key().source());
		final var attached = new ArrayList<>(supportManagement.getAttachments(municipalityId, errandId).stream().map(AttachedFile::of).toList());
		final var problems = new FileProblems();
		for (final var reference : candidate.files()) {
			final var lookup = fileArea.find(reference);
			switch (lookup.result()) {
				case FOUND -> uploadFile(municipalityId, errandId, lookup, attached, problems);
				case DIRECTORY_UNAVAILABLE -> throw new MigrationException("Filarean för %s gick inte att läsa".formatted(candidate.key().source().getDisplayName()));
				default -> problems.add(FileProblem.of(lookup.result()), lookup.name());
			}
		}
		problems.report(notes, maxFileSizeText());
	}

	/**
	 * Checks that the files can be found, without uploading anything.
	 */
	void check(final Candidate candidate, final RunContext context, final ReportNotes notes) {
		if (candidate.files().isEmpty()) {
			return;
		}
		final var fileArea = context.fileArea(candidate.key().source());
		final var problems = new FileProblems();
		candidate.files().stream()
			.map(fileArea::find)
			.filter(lookup -> lookup.result() != LookupResult.FOUND)
			.forEach(lookup -> problems.add(FileProblem.of(lookup.result()), lookup.name()));
		problems.report(notes, maxFileSizeText());
	}

	private void uploadFile(final String municipalityId, final String errandId, final FileArea.Lookup lookup, final List<AttachedFile> attached, final FileProblems problems) {
		final byte[] content;
		try {
			content = FileArea.read(lookup.path());
		} catch (final IOException e) {
			problems.add(FileProblem.UNREADABLE, lookup.name());
			return;
		}
		final var file = new AttachedFile(lookup.name(), sha256(content), content.length);
		if (attached.stream().anyMatch(file::isSameAs)) {
			return;
		}
		if (supportManagement.createAttachment(municipalityId, errandId, lookup.name(), content)) {
			attached.add(file);
		} else {
			problems.add(FileProblem.REJECTED, lookup.name());
		}
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
	 * @param name the file name
	 * @param hash the SHA-256 hash of the content, null when SupportManagement did not give it
	 * @param size the size in bytes, null when SupportManagement did not give it
	 */
	record AttachedFile(String name, String hash, Integer size) {

		static AttachedFile of(final ErrandAttachment attachment) {
			return new AttachedFile(attachment.getFileName(), attachment.getHash(), attachment.getFileSize());
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
		REJECTED("fil togs inte emot av SupportManagement", "filer togs inte emot av SupportManagement");

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
			names.computeIfAbsent(problem, key -> new ArrayList<>()).add(name);
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
