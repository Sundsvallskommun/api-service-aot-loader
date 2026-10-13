package se.sundsvall.aotloader.integration.relation;

import generated.se.sundsvall.relation.Relation;
import generated.se.sundsvall.relation.RelationPagedResponse;
import generated.se.sundsvall.relation.ResourceIdentifier;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import se.sundsvall.aotloader.support.MigrationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationIntegrationTest {

	private static final String MUNICIPALITY_ID = "2281";

	@Mock
	private RelationClient client;

	@Test
	void findRelationsKeepsOnlyExactMatches() {
		final var integration = new RelationIntegration(client);
		final var filter = "source.resourceId:'a' and target.resourceId:'b'";
		final var exact = relation("LINK", "a", "b");
		when(client.findRelations(MUNICIPALITY_ID, filter, RelationIntegration.LIMIT)).thenReturn(
			new RelationPagedResponse().relations(List.of(exact, relation("link", "a", "b"), relation("OTHER", "a", "b"), relation("LINK", "b", "a"), new Relation().type("LINK"))),
			new RelationPagedResponse(),
			null);

		assertThat(integration.findRelations(MUNICIPALITY_ID, "LINK", "a", "b")).hasSize(2);
		assertThat(integration.findRelations(MUNICIPALITY_ID, "LINK", "a", "b")).isEmpty();
		assertThat(integration.findRelations(MUNICIPALITY_ID, "LINK", "a", "b")).isEmpty();
		assertThatThrownBy(() -> integration.findRelations(MUNICIPALITY_ID, "LINK", "a'", "b")).isInstanceOf(MigrationException.class);
	}

	@Test
	void createRelation() {
		final var integration = new RelationIntegration(client);
		final var relation = relation("LINK", "a", "b");
		when(client.createRelation(MUNICIPALITY_ID, relation)).thenReturn(ResponseEntity.created(URI.create("/2281/relations/relation-id")).build());

		assertThat(integration.createRelation(MUNICIPALITY_ID, relation)).isEqualTo("relation-id");
	}

	private static Relation relation(final String type, final String source, final String target) {
		return new Relation().type(type)
			.source(new ResourceIdentifier().resourceId(source))
			.target(new ResourceIdentifier().resourceId(target));
	}
}
