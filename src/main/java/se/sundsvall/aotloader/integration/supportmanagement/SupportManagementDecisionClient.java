package se.sundsvall.aotloader.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Decision;
import generated.se.sundsvall.supportmanagement.DecisionTerm;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import se.sundsvall.aotloader.integration.supportmanagement.configuration.SupportManagementDecisionConfiguration;

import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.aotloader.integration.supportmanagement.configuration.SupportManagementConfiguration.CLIENT_ID;
import static se.sundsvall.aotloader.integration.supportmanagement.configuration.SupportManagementDecisionConfiguration.DECISION_CLIENT_ID;

/**
 * The calls the migration makes to the decisions of the permit errands (decision 70). SupportManagement takes a manual
 * decision only from an AD account, so these calls are sent by the AD account of the decisions, with the other headers
 * of {@link SupportManagementClient}.
 */
@FeignClient(name = CLIENT_ID, contextId = DECISION_CLIENT_ID, url = "${integration.support-management.url}", configuration = SupportManagementDecisionConfiguration.class)
public interface SupportManagementDecisionClient {

	@GetMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/decisions", produces = APPLICATION_JSON_VALUE)
	List<Decision> getDecisions(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId);

	@PostMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/decisions", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createDecision(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@RequestBody Decision decision);

	@PatchMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/decisions/{decisionId}", consumes = APPLICATION_JSON_VALUE, produces = APPLICATION_JSON_VALUE)
	Decision updateDecision(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@PathVariable String decisionId,
		@RequestBody Decision decision);

	@DeleteMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/decisions/{decisionId}", produces = ALL_VALUE)
	ResponseEntity<Void> deleteDecision(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@PathVariable String decisionId);

	@PostMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/decisions/{decisionId}/terms", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createDecisionTerm(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@PathVariable String decisionId,
		@RequestBody DecisionTerm term);

	@PostMapping(path = "/{municipalityId}/{namespace}/errands/{errandId}/decisions/{decisionId}/attachments/{attachmentId}", produces = ALL_VALUE)
	ResponseEntity<Void> linkDecisionAttachment(
		@PathVariable String municipalityId,
		@PathVariable String namespace,
		@PathVariable String errandId,
		@PathVariable String decisionId,
		@PathVariable String attachmentId);
}
