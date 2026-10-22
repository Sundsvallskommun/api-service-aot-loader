package se.sundsvall.aotloader.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.AttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Category;
import generated.se.sundsvall.supportmanagement.Config;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.ErrandAttachment;
import generated.se.sundsvall.supportmanagement.Investigation;
import generated.se.sundsvall.supportmanagement.Labels;
import generated.se.sundsvall.supportmanagement.MeasureType;
import generated.se.sundsvall.supportmanagement.PageErrand;
import generated.se.sundsvall.supportmanagement.Role;
import generated.se.sundsvall.supportmanagement.Status;
import generated.se.sundsvall.supportmanagement.UpdateErrandAttachmentRequest;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import se.sundsvall.aotloader.integration.supportmanagement.configuration.SupportManagementConfiguration;

import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static se.sundsvall.aotloader.integration.supportmanagement.configuration.SupportManagementConfiguration.CLIENT_ID;

/**
 * The calls the migration makes to SupportManagement. It never calls communication, conversations or decisions (R4).
 */
@FeignClient(name = CLIENT_ID, url = "${integration.support-management.url}", configuration = SupportManagementConfiguration.class)
public interface SupportManagementClient {

	/** The name of the part that holds the file of an attachment. */
	String ATTACHMENT_PART = "errandAttachment";

	@GetMapping(path = "/{municipalityId}/{namespace}/metadata/labels", produces = APPLICATION_JSON_VALUE)
	Labels getLabels(
		@PathVariable String municipalityId,
		@PathVariable String namespace);

	@GetMapping(path = "/{municipalityId}/{namespace}/namespace-config/action-config", produces = APPLICATION_JSON_VALUE)
	List<Config> getActionConfigs(
		@PathVariable String municipalityId,
		@PathVariable String namespace);

	@GetMapping(path = "/{municipalityId}/{namespace}/errands", produces = APPLICATION_JSON_VALUE)
	PageErrand findErrands(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@RequestParam("filter") String filter,
		@RequestParam("page") int page,
		@RequestParam("size") int size);

	@GetMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}", produces = APPLICATION_JSON_VALUE)
	Errand getErrand(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId);

	@PostMapping(path = "/{municipalityId}/{namespace}/errands", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createErrand(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@RequestBody Errand errand);

	@PatchMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	Errand updateErrand(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@RequestBody Errand errand);

	/**
	 * Deletes the errand with its attachments, investigations and measures. Its relations stay.
	 */
	@DeleteMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}", produces = ALL_VALUE)
	ResponseEntity<Void> deleteErrand(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId);

	@GetMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/attachments", produces = APPLICATION_JSON_VALUE)
	List<ErrandAttachment> getAttachments(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId);

	@PostMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/attachments", consumes = MULTIPART_FORM_DATA_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createAttachment(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@RequestParam(name = "received", required = false) String received,
		@RequestPart(ATTACHMENT_PART) MultipartFile errandAttachment);

	@PatchMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/attachments/{attachmentId}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	ErrandAttachment updateAttachment(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@PathVariable String attachmentId,
		@RequestBody UpdateErrandAttachmentRequest request);

	@GetMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/investigations", produces = APPLICATION_JSON_VALUE)
	List<Investigation> getInvestigations(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId);

	@PostMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/investigations", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createInvestigation(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@RequestBody Investigation investigation);

	@PostMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/investigations/{investigationId}/attachments/{attachmentId}", produces = ALL_VALUE)
	ResponseEntity<Void> linkInvestigationAttachment(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@PathVariable String investigationId,
		@PathVariable String attachmentId);

	@GetMapping(path = "/{municipalityId}/{namespace}/metadata/attachmentpurposes", produces = APPLICATION_JSON_VALUE)
	List<AttachmentPurpose> getAttachmentPurposes(
		@PathVariable String municipalityId,
		@PathVariable String namespace);

	@GetMapping(path = "/{municipalityId}/{namespace}/metadata/measuretypes", produces = APPLICATION_JSON_VALUE)
	List<MeasureType> getMeasureTypes(
		@PathVariable String municipalityId,
		@PathVariable String namespace);

	@GetMapping(path = "/{municipalityId}/{namespace}/metadata/roles", produces = APPLICATION_JSON_VALUE)
	List<Role> getRoles(
		@PathVariable String municipalityId,
		@PathVariable String namespace);

	@GetMapping(path = "/{municipalityId}/{namespace}/metadata/statuses", produces = APPLICATION_JSON_VALUE)
	List<Status> getStatuses(
		@PathVariable String municipalityId,
		@PathVariable String namespace);

	@GetMapping(path = "/{municipalityId}/{namespace}/metadata/categories", produces = APPLICATION_JSON_VALUE)
	List<Category> getCategories(
		@PathVariable String municipalityId,
		@PathVariable String namespace);
}
