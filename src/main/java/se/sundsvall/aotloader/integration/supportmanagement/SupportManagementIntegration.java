package se.sundsvall.aotloader.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.AttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Category;
import generated.se.sundsvall.supportmanagement.Config;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.ErrandAttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.Label;
import generated.se.sundsvall.supportmanagement.MeasureType;
import generated.se.sundsvall.supportmanagement.PageErrand;
import generated.se.sundsvall.supportmanagement.Role;
import generated.se.sundsvall.supportmanagement.Status;
import generated.se.sundsvall.supportmanagement.UpdateErrandAttachmentRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.integration.Fallbacks;
import se.sundsvall.aotloader.integration.FileRefusal;
import se.sundsvall.aotloader.support.ByteArrayMultipartFile;
import se.sundsvall.aotloader.support.FilterValues;
import se.sundsvall.aotloader.support.Locations;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class SupportManagementIntegration {

	static final int PAGE_SIZE = 100;
	static final int MAX_PAGES = 10;
	private static final String EXTERNAL_TAG = "Den externa taggen";

	private final SupportManagementClient client;
	private final String namespace;

	SupportManagementIntegration(final SupportManagementClient client, final AotLoaderProperties properties) {
		this.client = client;
		this.namespace = properties.namespace();
	}

	/**
	 * @return the label tree of the namespace
	 */
	public List<Label> getLabelTree(final String municipalityId) {
		return Optional.ofNullable(client.getLabels(municipalityId, namespace).getLabelStructure()).orElse(emptyList());
	}

	/**
	 * @return the action configurations of the namespace, active or not
	 */
	public List<Config> getActionConfigs(final String municipalityId) {
		return Optional.ofNullable(client.getActionConfigs(municipalityId, namespace)).orElse(emptyList());
	}

	/**
	 * Finds the errands, drafts included, that carry the external tag. The search names lifecycle, since a search that
	 * does not leaves the drafts out, and a rerun after an interruption would then create a second draft.
	 *
	 * @param  municipalityId     the municipality
	 * @param  key                the key of the tag
	 * @param  value              the value of the tag
	 * @return                    the errands that carry exactly that tag
	 * @throws MigrationException when more than {@value #MAX_PAGES} pages of errands match the search, whose rest would
	 *                            be missed
	 */
	public List<Errand> findErrandsByExternalTag(final String municipalityId, final String key, final String value) {
		final var filter = "externalTags.key:%s and externalTags.value:%s and (lifecycle:'DRAFT' or lifecycle:'ACTIVE')".formatted(FilterValues.quoted(key, EXTERNAL_TAG), FilterValues.quoted(value, EXTERNAL_TAG));
		final var errands = new ArrayList<Errand>();
		for (var page = 0; page < MAX_PAGES; page++) {
			final var result = client.findErrands(municipalityId, namespace, filter, page, PAGE_SIZE);
			final var content = Optional.ofNullable(result).map(PageErrand::getContent).orElse(emptyList());
			errands.addAll(content);
			if (content.size() < PAGE_SIZE || Boolean.TRUE.equals(result.getLast())) {
				// The filter joins the tags, so a key on one tag and a value on another would also match
				return errands.stream()
					.filter(errand -> hasTag(errand, key, value))
					.toList();
			}
		}
		throw new MigrationException("SupportManagement har fler än %d ärenden som matchar den externa taggen".formatted(MAX_PAGES * PAGE_SIZE));
	}

	/**
	 * @return the errand, or empty when it does not exist
	 */
	public Optional<Errand> getErrand(final String municipalityId, final String errandId) {
		return Fallbacks.unless(NOT_FOUND, Optional.empty(), () -> Optional.ofNullable(client.getErrand(municipalityId, namespace, errandId)));
	}

	/**
	 * @return the id of the created errand
	 */
	public String createErrand(final String municipalityId, final Errand errand) {
		return Locations.idOf(client.createErrand(municipalityId, namespace, errand), "ärendet");
	}

	/**
	 * Deletes the errand with its attachments, investigations and measures. Its relations stay, and are the caller's to
	 * delete.
	 *
	 * @return true when the errand was deleted, false when there was none with the id
	 */
	public boolean deleteErrand(final String municipalityId, final String errandId) {
		return Fallbacks.unless(NOT_FOUND, false, () -> {
			client.deleteErrand(municipalityId, namespace, errandId);
			return true;
		});
	}

	public Errand updateErrand(final String municipalityId, final String errandId, final Errand errand) {
		return client.updateErrand(municipalityId, namespace, errandId, errand);
	}

	public List<ErrandAttachment> getAttachments(final String municipalityId, final String errandId) {
		return Optional.ofNullable(client.getAttachments(municipalityId, namespace, errandId)).orElse(emptyList());
	}

	/**
	 * Uploads a file to the errand.
	 *
	 * @param  fileName           the name the file gets on the errand
	 * @param  content            the content of the file
	 * @param  received           when the file came in, which must not be later than now; null for the time of upload
	 * @return                    the id of the attachment, empty when SupportManagement refused the file itself
	 * @throws MigrationException when SupportManagement took the file but gave no id
	 */
	public Optional<String> createAttachment(final String municipalityId, final String errandId, final String fileName, final byte[] content, final OffsetDateTime received) {
		final ResponseEntity<Void> response;
		try {
			// In UTC, whose Z needs no encoding; a + in the query would be read as a blank
			response = client.createAttachment(municipalityId, namespace, errandId, received == null ? null : ISO_INSTANT.format(received),
				new ByteArrayMultipartFile(SupportManagementClient.ATTACHMENT_PART, fileName, content));
		} catch (final ThrowableProblem problem) {
			if (FileRefusal.isRefusedBySupportManagement(problem)) {
				return Optional.empty();
			}
			throw problem;
		}
		return Optional.of(Locations.idOf(response, "bilagan"));
	}

	/**
	 * Links an attachment of the errand to its investigation (decision 60).
	 *
	 * @return true when the attachment was linked now, false when it already was
	 */
	public boolean linkInvestigationAttachment(final String municipalityId, final String errandId, final String investigationId, final String attachmentId) {
		return Fallbacks.unless(CONFLICT, false, () -> {
			client.linkInvestigationAttachment(municipalityId, namespace, errandId, investigationId, attachmentId);
			return true;
		});
	}

	/**
	 * Sets what the attachment is for and when it came in.
	 *
	 * @param purposeId the id of the attachment purpose of the namespace, null to leave the purpose as it is
	 * @param received  when the file came in, which must not be later than now; null to leave it as it is
	 */
	public void updateAttachment(final String municipalityId, final String errandId, final String attachmentId, final String purposeId, final OffsetDateTime received) {
		final var request = new UpdateErrandAttachmentRequest().received(received);
		Optional.ofNullable(purposeId).ifPresent(id -> request.purpose(new ErrandAttachmentPurpose().id(id)));
		client.updateAttachment(municipalityId, namespace, errandId, attachmentId, request);
	}

	/**
	 * @return the investigations of the errand
	 */
	public List<Investigation> getInvestigations(final String municipalityId, final String errandId) {
		return Optional.ofNullable(client.getInvestigations(municipalityId, namespace, errandId)).orElse(emptyList());
	}

	/**
	 * @return                    the id of the investigation created for the errand
	 * @throws MigrationException when the response has no id
	 */
	public String createInvestigation(final String municipalityId, final String errandId, final Investigation investigation) {
		return Locations.idOf(client.createInvestigation(municipalityId, namespace, errandId, investigation), "utredningen");
	}

	/**
	 * @return the attachment purposes of the namespace
	 */
	public List<AttachmentPurpose> getAttachmentPurposes(final String municipalityId) {
		return Optional.ofNullable(client.getAttachmentPurposes(municipalityId, namespace)).orElse(emptyList());
	}

	/**
	 * @return the measure types of the namespace
	 */
	public List<MeasureType> getMeasureTypes(final String municipalityId) {
		return Optional.ofNullable(client.getMeasureTypes(municipalityId, namespace)).orElse(emptyList());
	}

	/**
	 * @return the roles of the namespace
	 */
	public List<Role> getRoles(final String municipalityId) {
		return Optional.ofNullable(client.getRoles(municipalityId, namespace)).orElse(emptyList());
	}

	/**
	 * @return the statuses of the namespace
	 */
	public List<Status> getStatuses(final String municipalityId) {
		return Optional.ofNullable(client.getStatuses(municipalityId, namespace)).orElse(emptyList());
	}

	/**
	 * @return the categories of the namespace, with their types
	 */
	public List<Category> getCategories(final String municipalityId) {
		return Optional.ofNullable(client.getCategories(municipalityId, namespace)).orElse(emptyList());
	}

	private static boolean hasTag(final Errand errand, final String key, final String value) {
		return Optional.ofNullable(errand.getExternalTags()).orElse(Set.of()).stream()
			.anyMatch(tag -> key.equals(tag.getKey()) && value.equals(tag.getValue()));
	}
}
