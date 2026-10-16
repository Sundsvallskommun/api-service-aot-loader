package se.sundsvall.aotloader.integration.relation;

import generated.se.sundsvall.relation.Relation;
import generated.se.sundsvall.relation.RelationPagedResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import se.sundsvall.aotloader.integration.relation.configuration.RelationConfiguration;

import static org.springframework.http.MediaType.ALL_VALUE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.aotloader.integration.relation.configuration.RelationConfiguration.CLIENT_ID;

@FeignClient(name = CLIENT_ID, url = "${integration.relation.url}", configuration = RelationConfiguration.class)
public interface RelationClient {

	@GetMapping(path = "/{municipalityId}/relations", produces = APPLICATION_JSON_VALUE)
	RelationPagedResponse findRelations(
		@PathVariable String municipalityId,
		@RequestParam("filter") String filter,
		@RequestParam("limit") int limit);

	@PostMapping(path = "/{municipalityId}/relations", consumes = APPLICATION_JSON_VALUE, produces = ALL_VALUE)
	ResponseEntity<Void> createRelation(
		@PathVariable String municipalityId,
		@RequestBody Relation relation);
}
