package se.sundsvall.aotloader.integration.relation;

import generated.se.sundsvall.relation.Relation;
import generated.se.sundsvall.relation.RelationPagedResponse;
import generated.se.sundsvall.relation.ResourceIdentifier;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.support.FilterValues;
import se.sundsvall.aotloader.support.Locations;
import se.sundsvall.aotloader.support.MigrationException;

import static java.util.Collections.emptyList;

@Component
public class RelationIntegration {

	static final int LIMIT = 100;

	private final RelationClient client;

	RelationIntegration(final RelationClient client) {
		this.client = client;
	}

	/**
	 * Finds the relations of the type from the source to the target. The relation service also writes the inverse of each
	 * relation, from the target to the source; that one is not returned.
	 *
	 * @return the relations with exactly that type, source and target
	 */
	public List<Relation> findRelations(final String municipalityId, final String type, final String sourceResourceId, final String targetResourceId) {
		if (!FilterValues.isSafe(sourceResourceId) || !FilterValues.isSafe(targetResourceId)) {
			throw new MigrationException("Ärendets id innehåller tecken som inte går att söka på");
		}
		final var filter = "source.resourceId:'%s' and target.resourceId:'%s'".formatted(sourceResourceId, targetResourceId);
		return Optional.ofNullable(client.findRelations(municipalityId, filter, LIMIT))
			.map(RelationPagedResponse::getRelations)
			.orElse(emptyList())
			.stream()
			// The filter is the relation service's to interpret, so the match is checked here as well
			.filter(relation -> type.equalsIgnoreCase(relation.getType()))
			.filter(relation -> resourceIdOf(relation.getSource()).equals(sourceResourceId))
			.filter(relation -> resourceIdOf(relation.getTarget()).equals(targetResourceId))
			.toList();
	}

	/**
	 * @return the id of the created relation
	 */
	public String createRelation(final String municipalityId, final Relation relation) {
		return Locations.idOf(client.createRelation(municipalityId, relation), "relationen");
	}

	private static String resourceIdOf(final ResourceIdentifier identifier) {
		return Optional.ofNullable(identifier).map(ResourceIdentifier::getResourceId).orElse("");
	}
}
