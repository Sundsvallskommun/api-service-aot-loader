package se.sundsvall.aotloader.service.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import generated.se.sundsvall.supportmanagement.ContactChannel;
import generated.se.sundsvall.supportmanagement.Errand;
import generated.se.sundsvall.supportmanagement.Errand.LifecycleEnum;
import generated.se.sundsvall.supportmanagement.ErrandLabel;
import generated.se.sundsvall.supportmanagement.ExternalTag;
import generated.se.sundsvall.supportmanagement.Measure;
import generated.se.sundsvall.supportmanagement.Priority;
import generated.se.sundsvall.supportmanagement.Stakeholder;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.InvestigationTexts.Part;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.MeasureData;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.PartyIds;
import se.sundsvall.aotloader.service.model.PartyType;
import se.sundsvall.aotloader.service.model.Person;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.MigrationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static se.sundsvall.aotloader.configuration.ClockConfiguration.SWEDISH_TIME;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;

class TargetMapperTest {

	private static final String ERRAND_ID = "11111111-1111-1111-1111-111111111111";
	private static final String MUNICIPALITY_ID = "2281";

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), SWEDISH_TIME);

	private final TargetMapper mapper = new TargetMapper(TestData.properties(), CLOCK);

	@Test
	void toNewDraft() {
		final var errand = newDraft();

		assertThat(errand.getTitle()).isEqualTo("Serveringstillstånd – Restaurang 1");
		// Draken reads the description as HTML
		assertThat(errand.getDescription()).isEqualTo("<p>Beskrivning</p>");
		assertThat(errand.getPriority()).isEqualTo(Priority.MEDIUM);
		assertThat(errand.getStatus()).isEqualTo("SOLVED");
		// As Draken closes an errand of AOT
		assertThat(errand.getResolution()).isEqualTo("CLOSED");
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
		// The place is in the parameters of the errand, under the keys pw-alkt reads and with the display names Draken writes
		assertThat(errand.getParameters()).extracting("key", "displayName", "values").containsExactly(
			tuple("alktObjektId", "Id i AlkT", List.of("1")),
			tuple("premisesName", "Serveringsställets namn", List.of("Restaurang 1")),
			tuple("premisesStreetAddress", "Serveringsställets gatuadress", List.of("Storgatan 1")),
			tuple("premisesPostalCode", "Serveringsställets postnummer", List.of("85230")),
			tuple("premisesPostalArea", "Serveringsställets postort", List.of("Sundsvall")),
			tuple("premisesPhone", "Serveringsställets telefon", List.of("060-2")),
			tuple("restaurantNumber", "Valt restaurangnummer", List.of("12345678")),
			tuple("premisesMunicipalityId", "Kommun", List.of("2281")));
	}

	@Test
	void theLegalBasisKeepsTheReferencesThatFitWhole() {
		final var first = "8 kap. 2 § alkohollagen (2010:1622)";
		final var many = String.join("; ", Collections.nCopies(10, first));

		assertThat(TargetMapper.legalBasisWithin(null)).isNull();
		assertThat(TargetMapper.legalBasisWithin(first)).isEqualTo(first);
		assertThat(TargetMapper.legalBasisWithin(many)).doesNotContain("se ärendet").isEqualTo(String.join("; ", Collections.nCopies(6, first)));
		assertThat(TargetMapper.legalBasisWithin("x".repeat(300))).hasSize(255).endsWith("…");
	}

	@Test
	void aSalesPlaceIsNamedSoAndItsPostalCodeHasNoBlanks() {
		final var candidate = Candidate.builder(new ItemKey(Source.ALKOL, TILLSTAND, "1"), ErrandType.TOBACCO_PERMIT)
			.place(new Place("Kiosken", "Torget 2", "852 31", "Sundsvall", null, "kiosk@example.com", null, List.of()))
			.build();

		final var parameters = mapper.toNewDraft(candidate, MUNICIPALITY_ID, new PartyIds(null, List.of()), TestData.labelIds()).getParameters();

		assertThat(parameters).extracting("key", "displayName", "values").containsExactly(
			tuple("premisesName", "Försäljningsställets namn", List.of("Kiosken")),
			tuple("premisesStreetAddress", "Försäljningsställets gatuadress", List.of("Torget 2")),
			tuple("premisesPostalCode", "Försäljningsställets postnummer", List.of("85231")),
			tuple("premisesPostalArea", "Försäljningsställets postort", List.of("Sundsvall")),
			tuple("premisesEmail", "Försäljningsställets e-post", List.of("kiosk@example.com")),
			tuple("premisesMunicipalityId", "Kommun", List.of("2281")));
	}

	@Test
	void holderAndContactPersonOfANewDraft() {
		final var stakeholders = newDraft().getStakeholders();

		assertThat(stakeholders).hasSize(4);
		assertThat(stakeholders.getFirst()).satisfies(holder -> {
			assertThat(holder.getRole()).isEqualTo("PRIMARY");
			assertThat(holder.getExternalId()).isEqualTo("party-id");
			assertThat(holder.getExternalIdType()).isEqualTo("COMPANY");
			assertThat(holder.getOrganizationName()).isEqualTo("Bolaget AB");
			assertThat(holder.getFirstName()).isNull();
			assertThat(holder.getAddress()).isEqualTo("Gatan 1");
			assertThat(holder.getZipCode()).isEqualTo("85230");
			assertThat(holder.getCity()).isEqualTo("Sundsvall");
			// Draken writes the country on every stakeholder
			assertThat(holder.getCountry()).isEqualTo("SVERIGE");
			assertThat(holder.getContactChannels()).extracting(ContactChannel::getType, ContactChannel::getValue).containsExactly(tuple("EMAIL", "bolag@example.com"), tuple("PHONE", "060-1"));
			// The organization number in clear text, as new errands in AOT have it
			assertThat(holder.getParameters()).extracting("key", "displayName", "values").containsExactly(tuple("organizationNumber", "Organisationsnummer", List.of("556677-8899")));
		});
		// The holder's contact person, without PBI, a person as Draken adds one by hand
		assertThat(stakeholders.get(1)).satisfies(contact -> {
			assertThat(contact.getRole()).isEqualTo("CONTACT");
			assertThat(contact.getFirstName()).isEqualTo("Kontakt Person");
			assertThat(contact.getExternalId()).isNull();
			assertThat(contact.getExternalIdType()).isEqualTo("PRIVATE");
			assertThat(contact.getCountry()).isEqualTo("SVERIGE");
			assertThat(contact.getContactChannels()).isNullOrEmpty();
			assertThat(contact.getParameters()).isNullOrEmpty();
		});
	}

	@Test
	void personsOfANewDraft() {
		final var stakeholders = newDraft().getStakeholders();

		assertThat(stakeholders.get(2)).satisfies(person -> {
			assertThat(person.getRole()).isEqualTo("CONTACT");
			assertThat(person.getFirstName()).isEqualTo("Person Ett");
			assertThat(person.getExternalId()).isEqualTo("person-party-id");
			assertThat(person.getExternalIdType()).isEqualTo("PRIVATE");
			assertThat(person.getCountry()).isEqualTo("SVERIGE");
			// The keys Draken writes, without display names
			assertThat(person.getParameters()).extracting("key", "displayName", "values").containsExactly(
				tuple("PBI", null, List.of("true")),
				tuple("PBI_SOURCE", null, List.of("MANUAL")),
				tuple("PBI_ROLE", null, List.of("Ledamot")));
		});
		assertThat(stakeholders.get(3)).satisfies(person -> {
			assertThat(person.getRole()).isEqualTo("CONTACT");
			assertThat(person.getExternalId()).isNull();
			assertThat(person.getExternalIdType()).isEqualTo("PRIVATE");
			assertThat(person.getParameters()).extracting("key", "values").containsExactly(tuple("PBI", List.of("true")), tuple("PBI_SOURCE", List.of("MANUAL")));
		});
	}

	@Test
	void aSoleTraderAmongThePersonsIsTheHolderMarkedInPlace() {
		final var soleTrader = new LegalId("191212121212", PartyType.PRIVATE);
		final var candidate = Candidate.builder(new ItemKey(Source.ALKOL, TILLSTAND, "1"), ErrandType.TOBACCO_PERMIT)
			.holder(new Holder(soleTrader, "Tolvans Tobak", null, null, null, null, null, null))
			.persons(List.of(new Person("Tolvan Tolvansson", soleTrader, false, List.of("Enskild firma")), new Person("Person Ett", TestData.PERSON_ID, false, List.of("VD"))))
			.build();
		final var personIds = new ArrayList<String>();
		personIds.add("holder-party-id");
		personIds.add("person-party-id");

		final var stakeholders = mapper.toNewDraft(candidate, MUNICIPALITY_ID, new PartyIds("holder-party-id", personIds), TestData.labelIds()).getStakeholders();

		// Without a source, since the holder is on the errand anyway
		assertThat(stakeholders).hasSize(2);
		assertThat(stakeholders.getFirst()).satisfies(holder -> {
			assertThat(holder.getRole()).isEqualTo("PRIMARY");
			assertThat(holder.getExternalId()).isEqualTo("holder-party-id");
			assertThat(holder.getParameters()).extracting("key", "values").containsExactly(tuple("PBI", List.of("true")), tuple("PBI_ROLE", List.of("Enskild firma")));
		});
		assertThat(stakeholders.get(1)).satisfies(person -> {
			assertThat(person.getExternalId()).isEqualTo("person-party-id");
			assertThat(person.getParameters()).extracting("key").containsExactly("PBI", "PBI_SOURCE", "PBI_ROLE");
		});
	}

	@Test
	void aHolderWithoutPartyIdHasTheTypeOfItsNumber() {
		final var company = Candidate.builder(new ItemKey(Source.ALKT, Kind.TILLSYN, "700"), ErrandType.ALCOHOL_INSPECTION)
			.holder(new Holder(TestData.HOLDER_ID, "Förra Bolaget AB", null, null, null, null, null, null))
			.build();
		final var withoutNumber = Candidate.builder(new ItemKey(Source.ALKT, Kind.TILLSYN, "701"), ErrandType.ALCOHOL_INSPECTION)
			.holder(new Holder(null, "Okänd Ägare", null, null, null, null, null, null))
			.build();

		// As Draken adds a stakeholder by hand: a company is named by its organization name, anyone else by the first name
		assertThat(mapper.toNewDraft(company, MUNICIPALITY_ID, new PartyIds(null, List.of()), TestData.labelIds()).getStakeholders()).singleElement().satisfies(holder -> {
			assertThat(holder.getExternalId()).isNull();
			assertThat(holder.getExternalIdType()).isEqualTo("COMPANY");
			assertThat(holder.getFirstName()).isNull();
			assertThat(holder.getOrganizationName()).isEqualTo("Förra Bolaget AB");
			assertThat(holder.getParameters()).isNullOrEmpty();
		});
		assertThat(mapper.toNewDraft(withoutNumber, MUNICIPALITY_ID, new PartyIds(null, List.of()), TestData.labelIds()).getStakeholders()).singleElement().satisfies(holder -> {
			assertThat(holder.getExternalIdType()).isEqualTo("PRIVATE");
			assertThat(holder.getFirstName()).isEqualTo("Okänd Ägare");
			assertThat(holder.getOrganizationName()).isEqualTo("Okänd Ägare");
		});
	}

	@Test
	void theRolesOfAPersonAreOneValue() {
		final var candidate = Candidate.builder(new ItemKey(Source.ALKT, TILLSTAND, "1"), ErrandType.SERVING_PERMIT)
			.persons(List.of(new Person("Person Ett", null, false, List.of("Ledamot", "Verkställande direktör", "Ledamot"))))
			.build();

		final var stakeholders = mapper.toNewDraft(candidate, MUNICIPALITY_ID, new PartyIds(null, List.of()), TestData.labelIds()).getStakeholders();

		// Draken reads only the first value of PBI_ROLE, so the positions are one value, each once
		assertThat(stakeholders).singleElement().satisfies(person -> assertThat(person.getParameters()).extracting("key", "values")
			.contains(tuple("PBI_ROLE", List.of("Ledamot, Verkställande direktör"))));
	}

	@Test
	void anOwningCompanyAmongThePersonsIsACompanyAndASoleTraderGetsNoOrganizationNumber() {
		final var soleTrader = new LegalId("191212121212", PartyType.PRIVATE);
		final var candidate = Candidate.builder(new ItemKey(Source.ALKT, TILLSTAND, "1"), ErrandType.SERVING_PERMIT)
			.holder(new Holder(soleTrader, "Tolvans Krog", null, null, null, null, null, null))
			.persons(List.of(
				new Person("Ägarbolaget AB", new LegalId("5599887766", PartyType.ENTERPRISE), false, List.of("Ägarbolag")),
				new Person("Okänt Bolag AB", new LegalId("5599887700", PartyType.ENTERPRISE), false, List.of())))
			.build();
		final var personIds = new ArrayList<String>();
		personIds.add("company-party-id");
		personIds.add(null);

		final var stakeholders = mapper.toNewDraft(candidate, MUNICIPALITY_ID, new PartyIds("holder-party-id", personIds), TestData.labelIds()).getStakeholders();

		assertThat(stakeholders).hasSize(3);
		// The personal identity number of a sole trader never goes to SupportManagement, and Draken names the holder by the
		// first name, since it is not a company
		assertThat(stakeholders.getFirst()).satisfies(holder -> {
			assertThat(holder.getExternalId()).isEqualTo("holder-party-id");
			assertThat(holder.getExternalIdType()).isEqualTo("PRIVATE");
			assertThat(holder.getFirstName()).isEqualTo("Tolvans Krog");
			assertThat(holder.getOrganizationName()).isEqualTo("Tolvans Krog");
			assertThat(holder.getParameters()).isNullOrEmpty();
		});
		// The name is also the first name, which Draken shows for a person with significant influence
		assertThat(stakeholders.get(1)).satisfies(company -> {
			assertThat(company.getRole()).isEqualTo("CONTACT");
			assertThat(company.getOrganizationName()).isEqualTo("Ägarbolaget AB");
			assertThat(company.getFirstName()).isEqualTo("Ägarbolaget AB");
			assertThat(company.getExternalId()).isEqualTo("company-party-id");
			assertThat(company.getExternalIdType()).isEqualTo("COMPANY");
			assertThat(company.getParameters()).extracting("key", "values").containsExactly(
				tuple("PBI", List.of("true")),
				tuple("PBI_SOURCE", List.of("MANUAL")),
				tuple("PBI_ROLE", List.of("Ägarbolag")),
				tuple("organizationNumber", List.of("559988-7766")));
		});
		// A company that Party does not know gets neither partyId nor organization number, but is a company
		assertThat(stakeholders.get(2)).satisfies(company -> {
			assertThat(company.getOrganizationName()).isEqualTo("Okänt Bolag AB");
			assertThat(company.getExternalId()).isNull();
			assertThat(company.getExternalIdType()).isEqualTo("COMPANY");
			assertThat(company.getParameters()).extracting("key").containsExactly("PBI", "PBI_SOURCE");
		});
	}

	/**
	 * A new draft of a permit with a holder, its contact person and two persons, the second without a party id.
	 */
	@Test
	void theParametersOfThePostThatPartyAssetsTakesOnlyCutAreTold() {
		final var asset = new AssetData(null, "Serveringstillstånd", null, null, Map.of("conditions", "x".repeat(256), "omrade", "x".repeat(255)));

		assertThat(TargetMapper.truncatedParameters(asset)).containsExactly("conditions");
	}

	@Test
	void theRemarksOfAVisitAreCompletedMeasuresOfTheMeasureTypeOfItsSettings() {
		final var visit = Candidate.builder(new ItemKey(Source.ALKT, Kind.TILLSYNSBESOK, "900"), ErrandType.ALCOHOL_INSPECTION_VISIT)
			.title("Tillsynsbesök – Restaurang 1")
			// The second visit has a time later than now, which the source has in error or for a planned inspection
			.measures(List.of(new MeasureData("Servering till minderårig", "Ungdomar i baren", LocalDateTime.of(2023, 11, 4, 23, 0)),
				new MeasureData("x".repeat(300), null, LocalDateTime.of(2026, 10, 15, 12, 1))))
			.build();

		final var errand = mapper.toNewDraft(visit, MUNICIPALITY_ID, new PartyIds(null, List.of()), TestData.labelIds());

		assertThat(errand.getMeasures()).extracting(Measure::getType, Measure::getTitle, Measure::getDescription, Measure::getAddedByUser, Measure::getAddedByRole, Measure::getStatus,
			Measure::getCompletedAt)
			.containsExactly(
				tuple("MEASURE_ALCOHOL_INSPECTION_VISIT", "Servering till minderårig", "Ungdomar i baren", "alkt-migration", "MEASURER", "COMPLETED", OffsetDateTime.parse("2023-11-04T23:00+01:00")),
				tuple("MEASURE_ALCOHOL_INSPECTION_VISIT", "x".repeat(254) + "…", null, "alkt-migration", "MEASURER", "COMPLETED", null));
		// A visit without remarks sends an empty list, so that an update of a draft removes the measures of an earlier run
		final var withoutRemarks = Candidate.builder(new ItemKey(Source.ALKT, Kind.TILLSYNSBESOK, "901"), ErrandType.ALCOHOL_INSPECTION_VISIT).build();
		assertThat(mapper.toDraftUpdate(withoutRemarks, MUNICIPALITY_ID, new PartyIds(null, List.of()), TestData.labelIds()).getMeasures()).isEmpty();
		// Another errand sends none, which leaves the measures of the errand as they are
		assertThat(newDraft().getMeasures()).isNull();
	}

	private Errand newDraft() {
		final var personIds = new ArrayList<String>();
		personIds.add("person-party-id");
		personIds.add(null);
		return mapper.toNewDraft(TestData.permit("1"), MUNICIPALITY_ID, new PartyIds("party-id", personIds), TestData.labelIds());
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
	void anInvestigationIsCompletedWhenTheSourceTellsWhenInSwedishTime() {
		final var completed = mapper.toInvestigation(new InvestigationData("Tillsyn – Restaurang 1", LocalDateTime.of(2024, 3, 31, 1, 30), LocalDateTime.of(2024, 3, 31, 3, 30)));
		final var notCompleted = mapper.toInvestigation(new InvestigationData("x".repeat(300), null, null));

		assertThat(completed.getStatus()).isEqualTo("COMPLETED");
		assertThat(completed.getTitle()).isEqualTo("Tillsyn – Restaurang 1");
		// Summer time begins between the two
		assertThat(completed.getStartedAt()).isEqualTo(OffsetDateTime.parse("2024-03-31T01:30+01:00"));
		assertThat(completed.getCompletedAt()).isEqualTo(OffsetDateTime.parse("2024-03-31T03:30+02:00"));
		assertThat(completed.getType()).isNull();
		assertThat(notCompleted.getStatus()).isEqualTo("ACTIVE");
		assertThat(notCompleted.getTitle()).hasSize(255);
		assertThat(notCompleted.getStartedAt()).isNull();
		assertThat(notCompleted.getCompletedAt()).isNull();
	}

	@Test
	void anInvestigationGetsTheTextsOfTheSourceAsItsConclusion() {
		final var withTexts = mapper.toInvestigation(new InvestigationData("Tillsyn", null, null,
			InvestigationTexts.of(new Part("Noteringar", "Klagomål & <oro>"), new Part("Händelser", "2020-01-10 Anteckning: text\n2020-01-20 Bild"), new Part("Beslut", "2020-02-01 Erinran"))));
		final var withoutTexts = mapper.toInvestigation(new InvestigationData("Tillsyn", null, null));

		// Draken shows only the conclusion, as HTML
		assertThat(withTexts.getConclusion()).isEqualTo("<p><strong>Noteringar</strong></p><p>Klagomål &amp; &lt;oro&gt;</p>"
			+ "<p><strong>Händelser</strong></p><p>2020-01-10 Anteckning: text</p><p>2020-01-20 Bild</p>"
			+ "<p><strong>Beslut</strong></p><p>2020-02-01 Erinran</p>");
		assertThat(withTexts.getDescription()).isNull();
		assertThat(withTexts.getSummary()).isNull();
		assertThat(withoutTexts.getConclusion()).isNull();
	}

	@Test
	void anInvestigationThatEndsLaterThanNowIsActiveWithoutAnEnd() {
		final var now = mapper.toInvestigation(new InvestigationData("Tillsyn", LocalDateTime.of(2026, 10, 15, 9, 0), LocalDateTime.of(2026, 10, 15, 12, 0)));
		final var later = mapper.toInvestigation(new InvestigationData("Tillsyn", LocalDateTime.of(2026, 10, 15, 9, 0), LocalDateTime.of(2026, 10, 15, 12, 1)));

		assertThat(now.getStatus()).isEqualTo("COMPLETED");
		assertThat(now.getCompletedAt()).isEqualTo(OffsetDateTime.parse("2026-10-15T12:00+02:00"));
		assertThat(later.getStatus()).isEqualTo("ACTIVE");
		assertThat(later.getStartedAt()).isEqualTo(OffsetDateTime.parse("2026-10-15T09:00+02:00"));
		assertThat(later.getCompletedAt()).isNull();
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
