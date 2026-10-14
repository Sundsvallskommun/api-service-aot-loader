package se.sundsvall.aotloader.service.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import generated.se.sundsvall.supportmanagement.ContactChannel;
import generated.se.sundsvall.supportmanagement.Errand.LifecycleEnum;
import generated.se.sundsvall.supportmanagement.ErrandLabel;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Priority;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.PartyIds;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.MigrationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;

class TargetMapperTest {

	private static final String ERRAND_ID = "11111111-1111-1111-1111-111111111111";
	private static final String MUNICIPALITY_ID = "2281";

	private final TargetMapper mapper = new TargetMapper(TestData.properties());

	@Test
	void toNewDraft() {
		final var personIds = new ArrayList<String>();
		personIds.add("person-party-id");
		personIds.add(null);

		final var errand = mapper.toNewDraft(TestData.permit("1"), MUNICIPALITY_ID, new PartyIds("party-id", personIds), TestData.labelIds());

		assertThat(errand.getTitle()).isEqualTo("Serveringstillstånd – Restaurang 1");
		assertThat(errand.getDescription()).isEqualTo("Beskrivning");
		assertThat(errand.getPriority()).isEqualTo(Priority.MEDIUM);
		assertThat(errand.getStatus()).isEqualTo("SOLVED");
		assertThat(errand.getClassification().getCategory()).isEqualTo("CATEGORY_SERVING_PERMIT");
		assertThat(errand.getClassification().getType()).isEqualTo("TYPE_SERVING_PERMIT");
		assertThat(errand.getLabels()).extracting(ErrandLabel::getId, ErrandLabel::getVersion).containsExactly(tuple("label-migrated", null), tuple("label-permanent-serving", null));
		assertThat(errand.getChannel()).isEqualTo("MIGRATION");
		assertThat(errand.getReporterUserId()).isEqualTo("alkt-migration");
		assertThat(errand.getBusinessRelated()).isTrue();
		assertThat(errand.getLifecycle()).isEqualTo(LifecycleEnum.DRAFT);
		assertThat(errand.getAssignedUserId()).isNull();
		assertThat(errand.getExternalTags()).extracting(ExternalTag::getKey, ExternalTag::getValue)
			.containsExactly(tuple("migreringsid", "ALKT-TILLSTAND-1"), tuple("diarienummer", "IAN-2019-1"));
		// The place is in the parameters of the errand, under the keys pw-alkt reads
		assertThat(errand.getParameters()).extracting("key", "displayName", "values").containsExactly(
			tuple("alktObjektId", "AlkT-id", List.of("1")),
			tuple("premisesName", "Ställets namn", List.of("Restaurang 1")),
			tuple("premisesStreetAddress", "Ställets gatuadress", List.of("Storgatan 1")),
			tuple("premisesPostalCode", "Ställets postnummer", List.of("85230")),
			tuple("premisesPostalArea", "Ställets postort", List.of("Sundsvall")),
			tuple("premisesPhone", "Ställets telefon", List.of("060-2")),
			tuple("restaurantNumber", "Serveringsställenummer", List.of("12345678")),
			tuple("premisesMunicipalityId", "Kommun", List.of("2281")));

		final var stakeholders = errand.getStakeholders();
		assertThat(stakeholders).hasSize(4);
		assertThat(stakeholders.getFirst()).satisfies(holder -> {
			assertThat(holder.getRole()).isEqualTo("PRIMARY");
			assertThat(holder.getExternalId()).isEqualTo("party-id");
			assertThat(holder.getExternalIdType()).isEqualTo("ENTERPRISE");
			assertThat(holder.getOrganizationName()).isEqualTo("Bolaget AB");
			assertThat(holder.getAddress()).isEqualTo("Gatan 1");
			assertThat(holder.getZipCode()).isEqualTo("85230");
			assertThat(holder.getCity()).isEqualTo("Sundsvall");
			assertThat(holder.getContactChannels()).extracting(ContactChannel::getType, ContactChannel::getValue).containsExactly(tuple("EMAIL", "bolag@example.com"), tuple("PHONE", "060-1"));
			assertThat(holder.getParameters()).isNullOrEmpty();
		});
		// The holder's contact person, without PBI
		assertThat(stakeholders.get(1)).satisfies(contact -> {
			assertThat(contact.getRole()).isEqualTo("CONTACT");
			assertThat(contact.getFirstName()).isEqualTo("Kontakt Person");
			assertThat(contact.getExternalId()).isNull();
			assertThat(contact.getContactChannels()).isNullOrEmpty();
			assertThat(contact.getParameters()).isNullOrEmpty();
		});
		assertThat(stakeholders.get(2)).satisfies(person -> {
			assertThat(person.getRole()).isEqualTo("CONTACT");
			assertThat(person.getFirstName()).isEqualTo("Person Ett");
			assertThat(person.getExternalId()).isEqualTo("person-party-id");
			assertThat(person.getExternalIdType()).isEqualTo("PRIVATE");
			assertThat(person.getParameters()).extracting("key", "displayName", "values").containsExactly(
				tuple("PBI", "Person med betydande inflytande", List.of("true")),
				tuple("befattning", "Befattning", List.of("Ledamot")));
		});
		assertThat(stakeholders.get(3)).satisfies(person -> {
			assertThat(person.getRole()).isEqualTo("CONTACT");
			assertThat(person.getExternalId()).isNull();
			assertThat(person.getExternalIdType()).isNull();
			assertThat(person.getParameters()).extracting("key", "values").containsExactly(tuple("PBI", List.of("true")));
		});
	}

	@Test
	void aDraftUpdateHasNoReporterAndALongParameterValueIsSplit() {
		final var candidate = Candidate.builder(new ItemKey(Source.ALKOL, TILLSTAND, "1"), ErrandType.TOBACCO_PERMIT)
			.labelPath("TAGROOT/MIGRATED_ERRAND")
			.parameters(List.of(new Parameter("noteringar", "Noteringar", List.of("a".repeat(3000) + " " + "b".repeat(10)))))
			.build();

		final var errand = mapper.toDraftUpdate(candidate, MUNICIPALITY_ID, new PartyIds(null, null), TestData.labelIds());

		assertThat(errand.getParameters().getFirst().getValues()).containsExactly("a".repeat(3000), "b".repeat(10));
		// An errand without a place belongs to the municipality of the run
		assertThat(errand.getParameters().getLast()).extracting("key", "values").containsExactly("premisesMunicipalityId", List.of("2281"));
		assertThat(errand.getLabels()).extracting(ErrandLabel::getId).containsExactly("label-migrated");
		assertThat(errand.getLifecycle()).isNull();
		assertThat(errand.getReporterUserId()).isNull();
		assertThat(errand.getStakeholders()).isEmpty();
	}

	@Test
	void aHolderWithoutAContactPersonGetsNoContact() {
		final var candidate = Candidate.builder(new ItemKey(Source.ALKT, TILLSTAND, "1"), ErrandType.SERVING_PERMIT)
			.labelPath("TAGROOT/MIGRATED_ERRAND")
			.holder(new Holder(TestData.HOLDER_ID, "Bolaget AB", null, null, null, null, null, " "))
			.build();

		final var errand = mapper.toDraftUpdate(candidate, MUNICIPALITY_ID, new PartyIds("party-id", List.of()), TestData.labelIds());

		assertThat(errand.getStakeholders()).singleElement().extracting(Stakeholder::getRole).isEqualTo("PRIMARY");
	}

	@Test
	void aLabelThatIsMissingInTheNamespace() {
		assertThatThrownBy(() -> mapper.toNewDraft(TestData.permit("1"), MUNICIPALITY_ID, new PartyIds(null, List.of()), Map.of()))
			.isInstanceOf(MigrationException.class)
			.hasMessage("Etiketten TAGROOT/MIGRATED_ERRAND finns inte i namespace");
	}

	@Test
	void toAsset() {
		final var asset = mapper.toAsset(TestData.permit("1"), MUNICIPALITY_ID, ERRAND_ID, "party-id", false);

		assertThat(asset.getAssetId()).isEqualTo(ERRAND_ID);
		assertThat(asset.getOrigin()).isEqualTo("SUPPORTMANAGEMENT");
		assertThat(asset.getPartyId()).isEqualTo("party-id");
		assertThat(asset.getType()).isEqualTo("AssetSERVING_PERMIT");
		assertThat(asset.getTitle()).isEqualTo("Titel SERVING_PERMIT");
		assertThat(asset.getDescription()).isEqualTo("Serveringstillstånd för Restaurang 1");
		assertThat(asset.getIssued()).isEqualTo(LocalDate.of(2019, 5, 1));
		assertThat(asset.getValidTo()).isNull();
		assertThat(asset.getStatus()).isEqualTo(Status.ACTIVE);
		assertThat(asset.getStatusReason()).isNull();
		assertThat(asset.getAdditionalParameters()).containsExactly(Map.entry("errandId", ERRAND_ID), Map.entry("migreradFran", "AlkT"), Map.entry("premisesMunicipalityId", "2281"));
	}

	@Test
	void aPlaceInAnAreaOfAnotherMunicipalityHasThatMunicipalityOnTheErrandAndThePost() {
		final var candidate = TestData.permit("1", "timrå");

		final var errand = mapper.toNewDraft(candidate, MUNICIPALITY_ID, new PartyIds("party-id", List.of()), TestData.labelIds());
		final var asset = mapper.toAsset(candidate, MUNICIPALITY_ID, ERRAND_ID, "party-id", false);

		assertThat(errand.getParameters()).filteredOn("key", "premisesMunicipalityId").extracting("values").containsExactly(List.of("2262"));
		assertThat(asset.getAdditionalParameters()).containsEntry("premisesMunicipalityId", "2262");
	}

	@Test
	void toAssetShortensLongValuesAndAddsTheTitleSuffix() {
		final var candidate = Candidate.builder(new ItemKey(Source.ALKOL, TILLSTAND, "1"), ErrandType.TOBACCO_PERMIT)
			.asset(new AssetData("detaljhandel", "Tobakstillstånd för " + "b".repeat(300), LocalDate.of(2020, 1, 1), LocalDate.of(2030, 1, 1),
				Map.of("conditions", "v".repeat(300), "annat", "x".repeat(300))))
			.build();

		final var asset = mapper.toAsset(candidate, MUNICIPALITY_ID, ERRAND_ID, "party-id", true);

		assertThat(asset.getTitle()).isEqualTo("Titel TOBACCO_PERMIT – detaljhandel");
		assertThat(asset.getStatus()).isEqualTo(Status.EXPIRED);
		assertThat(asset.getValidTo()).isEqualTo(LocalDate.of(2030, 1, 1));
		assertThat(asset.getAdditionalParameters().get("conditions")).hasSize(255).endsWith(" … (se ärendet)");
		assertThat(asset.getAdditionalParameters().get("annat")).hasSize(255).endsWith(" … (se ärendet)");
		assertThat(asset.getDescription()).hasSize(255).startsWith("Tobakstillstånd för b").endsWith(" … (se ärendet)");
	}

	@Test
	void toAssetOfAnErrandWithoutAsset() {
		final var candidate = Candidate.builder(new ItemKey(Source.ALKOL, ANMALAN, "1-FOLKOL"), ErrandType.FOLKOL_SALES_NOTIFICATION).build();

		assertThatThrownBy(() -> mapper.toAsset(candidate, MUNICIPALITY_ID, ERRAND_ID, "party-id", false)).isInstanceOf(MigrationException.class);
	}

	@Test
	void toContentUpdate() {
		final var wanted = mapper.toAsset(TestData.permit("1"), MUNICIPALITY_ID, ERRAND_ID, "party-id", false).validTo(LocalDate.of(2027, 6, 30));
		final var same = new Asset().title(wanted.getTitle()).validTo(wanted.getValidTo()).additionalParameters(Map.copyOf(wanted.getAdditionalParameters()));

		assertThat(mapper.toContentUpdate(same, wanted)).isEmpty();
		assertThat(mapper.toContentUpdate(same.type("Annan typ").description("Annan beskrivning"), wanted)).isEmpty();
		assertThat(mapper.toContentUpdate(new Asset().title("Gammal").validTo(wanted.getValidTo()).additionalParameters(wanted.getAdditionalParameters()), wanted))
			.contains(new AssetUpdateRequest().title(wanted.getTitle()));
		assertThat(mapper.toContentUpdate(new Asset().title(wanted.getTitle()).additionalParameters(wanted.getAdditionalParameters()), wanted))
			.contains(new AssetUpdateRequest().validTo(LocalDate.of(2027, 6, 30)));
		// PartyAssets replaces the whole map, so every parameter is sent
		assertThat(mapper.toContentUpdate(new Asset().title(wanted.getTitle()).validTo(wanted.getValidTo()), wanted))
			.contains(new AssetUpdateRequest().additionalParameters(wanted.getAdditionalParameters()));

		final var withoutTitleOrLastDay = new AssetCreateRequest().additionalParameters(Map.of("errandId", ERRAND_ID));
		assertThat(mapper.toContentUpdate(new Asset().title("Gammal").validTo(LocalDate.of(2020, 1, 1)).additionalParameters(Map.of("errandId", ERRAND_ID)), withoutTitleOrLastDay))
			.contains(new AssetUpdateRequest().title("").indefinitely(true));
	}

	@Test
	void sourceReferenceRelationAndActivation() {
		assertThat(mapper.toSourceReference(ERRAND_ID)).isEqualTo("LINK|" + ERRAND_ID + ";case;supportmanagement;AOT|");

		final var relation = mapper.toRelation("permit-errand", "inspection-errand");
		assertThat(relation.getType()).isEqualTo("LINK");
		assertThat(relation.getSource()).satisfies(source -> {
			assertThat(source.getResourceId()).isEqualTo("permit-errand");
			assertThat(source.getType()).isEqualTo("case");
			assertThat(source.getService()).isEqualTo("supportmanagement");
			assertThat(source.getNamespace()).isEqualTo("AOT");
		});
		assertThat(relation.getTarget().getResourceId()).isEqualTo("inspection-errand");

		assertThat(mapper.toActivation().getLifecycle()).isEqualTo(LifecycleEnum.ACTIVE);
		assertThat(mapper.toActivation().getTitle()).isNull();
	}

	@Test
	void roles() {
		assertThat(mapper.holderRole(ErrandType.SERVING_PERMIT)).isEqualTo("PRIMARY");
		assertThat(mapper.holderRole(ErrandType.TOBACCO_INSPECTION)).isEqualTo("INSPECTED");
		assertThat(mapper.holderRole(ErrandType.ALCOHOL_INSPECTION_VISIT)).isEqualTo("INSPECTED");
	}

	@Test
	void labelPaths() {
		assertThat(LabelPaths.normalize(" /CategoryRoot/ALCOHOL/ ")).isEqualTo("CATEGORYROOT/ALCOHOL");
		assertThat(LabelPaths.normalize(null)).isNull();
	}
}
