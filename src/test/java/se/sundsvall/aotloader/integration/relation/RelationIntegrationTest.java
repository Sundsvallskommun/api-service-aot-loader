package se.sundsvall.aotloader.integration.relation;

import generated.se.sundsvall.relation.PagingAndSortingMetaData;
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
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.NOT_FOUND;

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
	void findRelationsFromKeepsOnlyThoseFromTheSource() {
		final var integration = new RelationIntegration(client);
		final var toPermit = relation("LINK", "a", "asset");
		final var toInspection = relation("REFERENCE", "a", "c");
		when(client.findRelations(MUNICIPALITY_ID, "source.resourceId:'a'", RelationIntegration.LIMIT))
			.thenReturn(new RelationPagedResponse().relations(List.of(toPermit, toInspection, relation("LINK", "b", "a"), new Relation().type("LINK"))), (RelationPagedResponse) null);

		assertThat(integration.findRelationsFrom(MUNICIPALITY_ID, "a")).containsExactly(toPermit, toInspection);
		assertThat(integration.findRelationsFrom(MUNICIPALITY_ID, "a")).isEmpty();
		assertThatThrownBy(() -> integration.findRelationsFrom(MUNICIPALITY_ID, "a'")).isInstanceOf(MigrationException.class);
	}

	@Test
	void aSearchWithMoreHitsThanOneAnswerHoldsFails() {
		final var integration = new RelationIntegration(client);
		final var oneOfMany = new RelationPagedResponse().relations(List.of(relation("LINK", "a", "b"))).meta(new PagingAndSortingMetaData().totalRecords(101L));
		final var all = new RelationPagedResponse().relations(List.of(relation("LINK", "a", "b"))).meta(new PagingAndSortingMetaData().totalRecords(1L));
		when(client.findRelations(MUNICIPALITY_ID, "source.resourceId:'a' and target.resourceId:'b'", RelationIntegration.LIMIT)).thenReturn(oneOfMany, all);
		when(client.findRelations(MUNICIPALITY_ID, "source.resourceId:'a'", RelationIntegration.LIMIT)).thenReturn(oneOfMany);

		assertThatThrownBy(() -> integration.findRelations(MUNICIPALITY_ID, "LINK", "a", "b"))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Relationstjänsten har 101 relationer som matchar sökningen men visar bara 1");
		assertThat(integration.findRelations(MUNICIPALITY_ID, "LINK", "a", "b")).hasSize(1);
		assertThatThrownBy(() -> integration.findRelationsFrom(MUNICIPALITY_ID, "a")).isInstanceOf(MigrationException.class);
	}

	@Test
	void deleteRelationTellsWhetherThereWasOne() {
		final var integration = new RelationIntegration(client);
		when(client.deleteRelation(MUNICIPALITY_ID, "relation-id")).thenReturn(ResponseEntity.noContent().build());
		when(client.deleteRelation(MUNICIPALITY_ID, "gone")).thenThrow(Problem.valueOf(NOT_FOUND));
		when(client.deleteRelation(MUNICIPALITY_ID, "broken")).thenThrow(Problem.valueOf(BAD_GATEWAY));

		assertThat(integration.deleteRelation(MUNICIPALITY_ID, "relation-id")).isTrue();
		assertThat(integration.deleteRelation(MUNICIPALITY_ID, "gone")).isFalse();
		assertThatThrownBy(() -> integration.deleteRelation(MUNICIPALITY_ID, "broken")).isInstanceOf(ThrowableProblem.class);
		verify(client).deleteRelation(MUNICIPALITY_ID, "relation-id");
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
