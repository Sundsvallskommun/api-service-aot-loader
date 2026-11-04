package se.sundsvall.aotloader.service;

import com.google.common.hash.Hashing;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.service.model.FilePurpose;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.source.files.FileArea;
import se.sundsvall.aotloader.support.MigrationException;

import static java.util.stream.Collectors.toSet;
import static se.sundsvall.aotloader.configuration.ClockConfiguration.SWEDISH_TIME;

/**
 * Uploads the files of an item to its draft errand, or in a dry run checks that they can be found (step 3 of section
 * 6). The file names of the files that are not uploaded are listed in the report, for the business to find the files
 * (G14).
 */
@Component
class FileUploader {

	private final SupportManagementIntegration supportManagement;
	private final ItemFiles itemFiles;
	private final Clock clock;

	FileUploader(final SupportManagementIntegration supportManagement, final AotLoaderProperties properties, final Clock clock) {
		this.supportManagement = supportManagement;
		this.itemFiles = new ItemFiles(properties);
		this.clock = clock;
	}

	/**
	 * Uploads the files to the draft, each with its purpose and the date it came in (decision 40). A file already on the
	 * errand, with the same name and content, is not uploaded again, but gets the purpose and date it lacks. A file that
	 * cannot be uploaded for good is listed in the report.
	 *
	 * @return                    the attachments of the files on the errand, uploaded now or found there by name and
	 *                            content, and which of them are material of the investigation (decision 60)
	 * @throws MigrationException when the file area or a file cannot be read, or a purpose of a file was not read for the
	 *                            municipality, so that the step is made again in the next run
	 */
	UploadedFiles upload(final Candidate candidate, final RunContext context, final String municipalityId, final String errandId, final ReportNotes notes) {
		if (candidate.files().references().isEmpty()) {
			return UploadedFiles.NONE;
		}
		final var fileArea = context.fileArea(candidate.key().source());
		final var attached = new ArrayList<>(supportManagement.getAttachments(municipalityId, errandId).stream().map(AttachedFile::of).toList());
		final var problems = new FileProblems();
		final var attachmentIds = new LinkedHashMap<FileReference, String>();
		final var material = new LinkedHashSet<String>();
		for (final var reference : candidate.files().references()) {
			final var lookup = ItemFiles.find(fileArea, reference, candidate);
			final var problem = FileProblem.of(reference, lookup);
			if (problem.isPresent()) {
				problems.add(problem.get(), lookup.name());
				continue;
			}
			final var details = candidate.files().detailsOf(reference);
			uploadFile(municipalityId, errandId, lookup, wanted(details, context, municipalityId), attached, problems).ifPresent(id -> {
				attachmentIds.put(reference, id);
				if (isMaterial(candidate, reference)) {
					material.add(id);
				}
			});
		}
		itemFiles.reportAndFailOnUnreadable(problems, notes, candidate);
		return new UploadedFiles(attachmentIds, material);
	}

	/**
	 * Links the errand's attachments of the files that are material of its investigation to the investigation (decision
	 * 60). An attachment already linked is left as it is. An errand without an investigation links nothing.
	 *
	 * @param material the ids of the attachments of the material, as the upload gives them; a file that is not on the
	 *                 errand is not among them, and the upload lists it
	 */
	void linkToInvestigation(final Candidate candidate, final String municipalityId, final String errandId, final Set<String> material, final ReportNotes notes) {
		if (candidate.investigation() == null || material.isEmpty()) {
			return;
		}
		final var investigation = supportManagement.getInvestigations(municipalityId, errandId).stream().findFirst();
		if (investigation.isEmpty()) {
			return;
		}
		final var linked = Optional.ofNullable(investigation.get().getAttachments()).orElse(List.of()).stream().map(ErrandAttachment::getId).collect(toSet());
		material.stream()
			.filter(attachmentId -> !linked.contains(attachmentId))
			.forEach(attachmentId -> supportManagement.linkInvestigationAttachment(municipalityId, errandId, investigation.get().getId(), attachmentId));
		notes.line(investigationMaterial(material.size()));
	}

	private static String investigationMaterial(final int count) {
		return "Underlag till utredningen: " + (count == 1 ? "1 fil" : count + " filer");
	}

	/**
	 * @return true when the file is material of the investigation, and the candidate has an investigation to link it to
	 */
	private static boolean isMaterial(final Candidate candidate, final FileReference reference) {
		return candidate.investigation() != null && candidate.files().detailsOf(reference).investigation();
	}

	/**
	 * Checks that the files can be found and would be uploaded, without uploading anything, and tells which would go on
	 * the post in PartyAssets.
	 *
	 * @return the files that would be uploaded, the files of the item that have no problem
	 */
	Set<FileReference> check(final Candidate candidate, final RunContext context, final ReportNotes notes) {
		if (candidate.files().references().isEmpty()) {
			return Set.of();
		}
		final var fileArea = context.fileArea(candidate.key().source());
		final var problems = new FileProblems();
		final var uploadable = new LinkedHashSet<FileReference>();
		var material = 0;
		for (final var reference : candidate.files().references()) {
			final var lookup = fileArea.find(reference);
			final var problem = FileProblem.of(reference, lookup);
			problem.ifPresent(found -> problems.add(found, lookup.name()));
			if (problem.isEmpty()) {
				uploadable.add(reference);
				if (isMaterial(candidate, reference)) {
					material++;
				}
			}
		}
		if (candidate.asset() != null && !candidate.files().onPost().isEmpty()) {
			notes.line("Till %s i PartyAssets: %s".formatted(AssetWords.of(candidate.key().kind()).definite(), AssetFileUploader.categoryCounts(candidate, context, context.municipalityId())));
		}
		if (candidate.files().references().stream().anyMatch(reference -> isMaterial(candidate, reference))) {
			notes.line(investigationMaterial(material));
		}
		problems.report(notes, itemFiles.maxFileSizeText());
		return uploadable;
	}

	/**
	 * @return the id of the attachment of the file, uploaded now or already on the errand; empty when it was not uploaded
	 */
	private Optional<String> uploadFile(final String municipalityId, final String errandId, final FileArea.Lookup lookup, final Wanted wanted, final List<AttachedFile> attached,
		final FileProblems problems) {
		final var read = itemFiles.read(lookup, problems);
		if (read.isEmpty()) {
			return Optional.empty();
		}
		final var content = read.get();
		final var file = new AttachedFile(lookup.name(), sha256(content), content.length);
		final var existing = attached.stream().filter(file::isSameAs).findFirst();
		if (existing.isPresent()) {
			completeAttachment(municipalityId, errandId, existing.get(), wanted);
			return Optional.ofNullable(existing.get().id());
		}
		final var id = supportManagement.createAttachment(municipalityId, errandId, lookup.name(), content, wanted.received());
		if (id.isEmpty()) {
			problems.add(FileProblem.REJECTED, lookup.name());
			return Optional.empty();
		}
		Optional.ofNullable(wanted.purposeId()).ifPresent(purposeId -> supportManagement.updateAttachment(municipalityId, errandId, id.get(), purposeId, null));
		attached.add(new AttachedFile(file.name(), file.hash(), file.size(), id.get(), wanted.purposeId(), wanted.received()));
		return id;
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

	/**
	 * @return the SHA-256 hash of the content, hex encoded in lower case, as SupportManagement gives it each attachment
	 */
	static String sha256(final byte[] content) {
		return Hashing.sha256().hashBytes(content).toString();
	}
}
