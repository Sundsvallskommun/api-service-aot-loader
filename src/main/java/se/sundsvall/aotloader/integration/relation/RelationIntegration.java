package se.sundsvall.aotloader.integration.relation;

import generated.se.sundsvall.relation.PagingAndSortingMetaData;
import generated.se.sundsvall.relation.Relation;
import generated.se.sundsvall.relation.RelationPagedResponse;
import generated.se.sundsvall.relation.ResourceIdentifier;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.integration.Fallbacks;
import se.sundsvall.aotloader.support.FilterValues;
import se.sundsvall.aotloader.support.Locations;
import se.sundsvall.aotloader.support.MigrationException;

import static java.util.Collections.emptyList;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class RelationIntegration {

	static final int LIMIT = 100;
	private static final String ERRAND_ID = "Ärendets id";

	private final RelationClient client;

	RelationIntegration(final RelationClient client) {
		this.client = client;
	}

	/**
	 * Finds the relations of the type from the source to the target. The relation service also writes the inverse of each
	 * relation, from the target to the source; that one is not returned.
	 *
	 * @return                    the relations with exactly that type, source and target
	 * @throws MigrationException when the relation service has more than {@value #LIMIT} relations that match the search
	 */
	public List<Relation> findRelations(final String municipalityId, final String type, final String sourceResourceId, final String targetResourceId) {
		final var filter = "source.resourceId:%s and target.resourceId:%s".formatted(FilterValues.quoted(sourceResourceId, ERRAND_ID), FilterValues.quoted(targetResourceId, ERRAND_ID));
		return relationsOf(client.findRelations(municipalityId, filter, LIMIT)).stream()
			// The filter is the relation service's to interpret, so the match is checked here as well
			.filter(relation -> type.equalsIgnoreCase(relation.getType()))
			.filter(relation -> resourceIdOf(relation.getSource()).equals(sourceResourceId))
			.filter(relation -> resourceIdOf(relation.getTarget()).equals(targetResourceId))
			.toList();
	}

	/**
	 * Finds the relations from the source, of every type and to every target. The inverse the relation service writes for
	 * a relation to the source is among them, as a relation from the source.
	 *
	 * @return                    the relations with exactly that source
	 * @throws MigrationException when the relation service has more than {@value #LIMIT}
	 */
	public List<Relation> findRelationsFrom(final String municipalityId, final String sourceResourceId) {
		return relationsOf(client.findRelations(municipalityId, "source.resourceId:" + FilterValues.quoted(sourceResourceId, ERRAND_ID), LIMIT)).stream()
			.filter(relation -> resourceIdOf(relation.getSource()).equals(sourceResourceId))
			.toList();
	}

	/**
	 * @return the id of the created relation
	 */
	public String createRelation(final String municipalityId, final Relation relation) {
		return Locations.idOf(client.createRelation(municipalityId, relation), "relationen");
	}

	/**
	 * Deletes the relation, and with it the inverse the relation service wrote for it.
	 *
	 * @return true when the relation was deleted, false when there was none with the id
	 */
	public boolean deleteRelation(final String municipalityId, final String id) {
		return Fallbacks.unless(NOT_FOUND, false, () -> {
			client.deleteRelation(municipalityId, id);
			return true;
		});
	}

	// A search whose hits do not fit in one answer would leave relations out, which the duplicate check and the cleanup
	// must not miss
	private static List<Relation> relationsOf(final RelationPagedResponse response) {
		final var relations = Optional.ofNullable(response).map(RelationPagedResponse::getRelations).orElse(emptyList());
		final var total = Optional.ofNullable(response).map(RelationPagedResponse::getMeta).map(PagingAndSortingMetaData::getTotalRecords).orElse(0L);
		if (total > relations.size()) {
			throw new MigrationException("Relationstjänsten har %d relationer som matchar sökningen men visar bara %d".formatted(total, relations.size()));
		}
		return relations;
	}

	private static String resourceIdOf(final ResourceIdentifier identifier) {
		return Optional.ofNullable(identifier).map(ResourceIdentifier::getResourceId).orElse("");
	}
}
