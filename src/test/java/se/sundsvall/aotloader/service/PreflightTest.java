package se.sundsvall.aotloader.service;

import generated.se.sundsvall.supportmanagement.AttachmentPurpose;
import generated.se.sundsvall.supportmanagement.Config;
import generated.se.sundsvall.supportmanagement.DecisionOutcome;
import generated.se.sundsvall.supportmanagement.Label;
import generated.se.sundsvall.supportmanagement.LabelAttribute;
import generated.se.sundsvall.supportmanagement.MeasureType;
import generated.se.sundsvall.supportmanagement.Role;
import generated.se.sundsvall.supportmanagement.Status;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.util.unit.DataSize;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.ErrandTypeSettings;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.FileAreaDirectories;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Roles;
import se.sundsvall.aotloader.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FilePurpose;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.FilePurposes;
import se.sundsvall.aotloader.source.SourceDatabases;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.DOKUMENTATION;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYNSBESOK;

@ExtendWith(MockitoExtension.class)
class PreflightTest {

	private static final String MUNICIPALITY_ID = "2281";

	@Mock
	private SupportManagementIntegration supportManagement;

	@Mock
	private SourceDatabases sourceDatabases;

	@TempDir
	private Path directory;

	@BeforeEach
	void namespaceHasEveryPurposeAndTheMeasureTypesOfTheInspections() {
		lenient().when(supportManagement.getAttachmentPurposes(MUNICIPALITY_ID)).thenReturn(FilePurposes.ALL.stream()
			.map(name -> new AttachmentPurpose().id("id-" + name).name(name).displayName(name.toLowerCase(Locale.ROOT)))
			.toList());
		lenient().when(supportManagement.getMeasureTypes(MUNICIPALITY_ID))
			.thenReturn(List.of(new MeasureType().name("MEASURE_ALCOHOL_INSPECTION_VISIT"), new MeasureType().name("MEASURE_TOBACCO_INSPECTION_VISIT")));
		lenient().when(supportManagement.getRoles(MUNICIPALITY_ID))
			.thenReturn(List.of(new Role().name("PRIMARY"), new Role().name("CONTACT"), new Role().name("INSPECTED"), new Role().name("MEASURER")));
		lenient().when(supportManagement.getStatuses(MUNICIPALITY_ID)).thenReturn(List.of(new Status().name("NEW"), new Status().name("SOLVED")));
	}

	@Test
	void theRolesTheStatusAndTheClassificationsOfTheErrandsMustExist() {
		// The label tree without the categories and types the classifications are taken from
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true).stream()
			.map(root -> root.labels(root.getLabels().stream().filter(label -> !label.getResourcePath().contains("/CATEGORY_")).toList()))
			.toList());
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement, sourceDatabases);
		when(supportManagement.getRoles(MUNICIPALITY_ID)).thenReturn(List.of(new Role().name("PRIMARY")));
		when(supportManagement.getStatuses(MUNICIPALITY_ID)).thenReturn(List.of(new Status().name("SOLVED").deprecated(true)));

		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN))).problems()).containsExactly(
			"Klassificeringen CATEGORY_ALCOHOL_INSPECTION/TYPE_ALCOHOL_INSPECTION finns inte som kategori och typ i etikettträdet, CATEGORYROOT/CATEGORY_ALCOHOL_INSPECTION/TYPE_ALCOHOL_INSPECTION (aot-loader.errand-types, beslut 5)",
			"Klassificeringen CATEGORY_SERVING_PERMIT/TYPE_SERVING_PERMIT finns inte som kategori och typ i etikettträdet, CATEGORYROOT/CATEGORY_SERVING_PERMIT/TYPE_SERVING_PERMIT (aot-loader.errand-types, beslut 5)",
			"Rollen CONTACT finns inte i namnrymden, och ärendena kan inte få den (aot-loader.roles)",
			"Rollen INSPECTED finns inte i namnrymden, och ärendena kan inte få den (aot-loader.roles)",
			"Statusen SOLVED finns inte i namnrymden, eller är inaktuell (aot-loader.errand-status, beslut 5)");

		when(supportManagement.getStatuses(MUNICIPALITY_ID)).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));
		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems()).contains(
			"Statusarna i SupportManagement gick inte att hämta: 502 BAD_GATEWAY Fel");
	}

	@Test
	void theVisitsNeedTheirMeasureTypeAndTheRoleOfTheMeasures() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement, sourceDatabases);
		final var visits = Map.of(Source.ALKT, Set.of(TILLSYNSBESOK), Source.ALKOL, Set.of(TILLSYNSBESOK));

		assertThat(preflight.check(MUNICIPALITY_ID, visits).problems()).isEmpty();

		when(supportManagement.getMeasureTypes(MUNICIPALITY_ID)).thenReturn(List.of(new MeasureType().name("MEASURE_ALCOHOL_INSPECTION_VISIT")));
		assertThat(preflight.check(MUNICIPALITY_ID, visits).problems())
			.containsExactly("Åtgärdstypen MEASURE_TOBACCO_INSPECTION_VISIT finns inte i namnrymden, så tillsynsbesökens anmärkningar kan inte bli åtgärder (beslut 57)");
		// A run without visits gives no measures and needs no measure type
		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems()).isEmpty();

		when(supportManagement.getMeasureTypes(MUNICIPALITY_ID)).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));
		assertThat(preflight.check(MUNICIPALITY_ID, visits).problems()).containsExactly("Åtgärdstyperna i SupportManagement gick inte att hämta: 502 BAD_GATEWAY Fel");
	}

	@Test
	void theRoleOfTheMeasuresMustBeARoleOfTheNamespace() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement, sourceDatabases);
		final var visits = Map.of(Source.ALKT, Set.of(TILLSYNSBESOK));

		// SupportManagement refuses a measure added by a role the namespace lacks
		when(supportManagement.getRoles(MUNICIPALITY_ID)).thenReturn(List.of(new Role().name("PRIMARY")));
		assertThat(preflight.check(MUNICIPALITY_ID, visits).problems())
			.containsExactly("Rollen MEASURER finns inte i namnrymden, och ärendena kan inte få den (aot-loader.roles)");

		when(supportManagement.getRoles(MUNICIPALITY_ID)).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));
		assertThat(preflight.check(MUNICIPALITY_ID, visits).problems()).containsExactly("Rollerna i SupportManagement gick inte att hämta: 502 BAD_GATEWAY Fel");
	}

	@Test
	void purposesThatAreMissingOrDeprecatedStopARealRun() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		when(supportManagement.getAttachmentPurposes(MUNICIPALITY_ID)).thenReturn(Stream.concat(
			FilePurposes.ALL.stream()
				.filter(name -> !FilePurposes.STATEMENT_REQUEST.equals(name) && !FilePurposes.POLICE_REQUEST.equals(name))
				.map(name -> new AttachmentPurpose().id("id-" + name).name(name)),
			Stream.of(
				new AttachmentPurpose().id("id-old").name(FilePurposes.POLICE_REQUEST).deprecated(true),
				new AttachmentPurpose().id("id-budget").name("BUDGET"),
				new AttachmentPurpose().name(FilePurposes.STATEMENT_REQUEST)))
			.toList());
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement, sourceDatabases);

		final var result = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND)));

		assertThat(result.problems()).containsExactly(
			"Syftet REFERRAL_POLICE_REQUEST för bilagor är inaktuellt i namnrymden (beslut 40)",
			"Syftet STATEMENT_REQUEST för bilagor finns inte i namnrymden (beslut 40)");
		assertThat(result.purposes().get(MUNICIPALITY_ID)).hasSize(FilePurposes.ALL.size() - 2)
			.containsEntry(FilePurposes.SERVING_AREA_DRAWING, new FilePurpose("id-SERVING_AREA_DRAWING", FilePurposes.SERVING_AREA_DRAWING, null))
			.doesNotContainKeys("BUDGET", FilePurposes.POLICE_REQUEST, FilePurposes.STATEMENT_REQUEST);
	}

	@Test
	void aSourceAccountThatMayWriteStopsARealRun() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		when(sourceDatabases.writeAccess(Source.ALKT)).thenReturn(Optional.of("kan ändra innehållet i 3 tabeller eller vyer och ändra databasens struktur"));
		when(sourceDatabases.writeAccess(Source.ALKOL)).thenThrow(new CannotGetJdbcConnectionException("Login failed"));
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement, sourceDatabases);

		final var result = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND), Source.ALKOL, Set.of(TILLSTAND)));

		assertThat(result.problems()).containsExactlyInAnyOrder(
			"Kontot för källdatabasen för AlkT kan ändra innehållet i 3 tabeller eller vyer och ändra databasens struktur, men källan får bara läsas (K7)",
			"Kontots behörigheter i källdatabasen för AlkOL gick inte att kontrollera: CannotGetJdbcConnectionException: Login failed");
	}

	@Test
	void purposesThatCannotBeRead() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		when(supportManagement.getAttachmentPurposes(MUNICIPALITY_ID)).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement, sourceDatabases);

		final var result = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND)));

		assertThat(result.problems()).containsExactly("Syftena för bilagor i SupportManagement gick inte att hämta: 502 BAD_GATEWAY Fel");
		assertThat(result.purposes().get(MUNICIPALITY_ID)).isEmpty();
	}

	@Test
	void everythingInPlace() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement, sourceDatabases);

		final var result = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN), Source.ALKOL, Set.of(TILLSTAND, ANMALAN, TILLSYN)));

		assertThat(result.problems()).isEmpty();
		assertThat(result.labelIds()).containsOnlyKeys(MUNICIPALITY_ID);
		assertThat(result.labelIds().get(MUNICIPALITY_ID)).containsEntry("TAGROOT/MIGRATED_ERRAND", "id-TAGROOT/MIGRATED_ERRAND")
			.containsEntry("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/TASTING", "id-CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/TASTING");
		assertThat(result.purposes().get(MUNICIPALITY_ID)).hasSize(FilePurposes.ALL.size())
			.containsEntry(FilePurposes.SERVING_AREA_DRAWING, new FilePurpose("id-SERVING_AREA_DRAWING", FilePurposes.SERVING_AREA_DRAWING, "serving_area_drawing"));
	}

	@Test
	void thePermitsGetTheirDecisionsWithTheOutcomesOfTheNamespace() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		when(supportManagement.getDecisionOutcomes(MUNICIPALITY_ID)).thenReturn(List.of(new DecisionOutcome().name("APPROVAL").displayName("Bifall"),
			new DecisionOutcome().name("APPROVAL_WITH_CONDITIONS").displayName(" "), new DecisionOutcome().displayName("Utan namn")));
		final var preflight = new Preflight(TestData.propertiesWithDecisions(directory.toString(), directory.toString(), " ab12cde "), supportManagement, sourceDatabases);

		final var result = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND)));

		assertThat(result.problems()).isEmpty();
		// An outcome without a display name stands by its name
		assertThat(result.decisionOutcomes()).containsExactly(entry(MUNICIPALITY_ID, Map.of("APPROVAL", "Bifall", "APPROVAL_WITH_CONDITIONS", "APPROVAL_WITH_CONDITIONS")));
		// A run without permits that get decisions does not read the outcomes
		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSYN))).decisionOutcomes()).containsExactly(entry(MUNICIPALITY_ID, Map.of()));
		verify(supportManagement).getDecisionOutcomes(MUNICIPALITY_ID);
	}

	@Test
	void theDecisionsNeedTheirAdAccountHeadingAndOutcomes() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		when(supportManagement.getDecisionOutcomes(MUNICIPALITY_ID)).thenReturn(List.of(new DecisionOutcome().name("APPROVAL")));
		final var withoutAccount = TestData.propertiesWithDecisions(directory.toString(), directory.toString(), null);
		final var types = new HashMap<>(withoutAccount.errandTypes());
		final var serving = types.get(ErrandType.SERVING_PERMIT);
		types.put(ErrandType.SERVING_PERMIT, new ErrandTypeSettings(serving.category(), serving.type(), serving.measureType(), serving.assetType(), serving.assetTitle(), serving.labels(),
			serving.subtypeLabels(), serving.sourceErrandTypes(), serving.onlyPlacesWithPermit(), serving.decisionType(), " "));
		final var properties = new AotLoaderProperties(withoutAccount.namespace(), withoutAccount.sentBy(), withoutAccount.reporterUserId(), withoutAccount.channel(),
			withoutAccount.errandStatus(), withoutAccount.errandResolution(), withoutAccount.roles(), types, withoutAccount.databases(), withoutAccount.fileArea(), withoutAccount.maxFileSize(),
			withoutAccount.municipalities(), null);

		assertThat(new Preflight(properties, supportManagement, sourceDatabases).check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems()).containsExactly(
			"AD-kontot som tillståndens beslut skrivs med är inte angivet (aot-loader.decision-ad-account, beslut 70)",
			"Rubrik för beslutet saknas för SERVING_PERMIT (aot-loader.errand-types, beslut 70)",
			"Beslutsutfallet APPROVAL_WITH_CONDITIONS finns inte i namnrymden, så tillståndens beslut kan inte få det (beslut 70)");

		// The value of the header is the account alone
		final var withHeader = TestData.propertiesWithDecisions(directory.toString(), directory.toString(), "ab12cde; type=adAccount");
		when(supportManagement.getDecisionOutcomes(MUNICIPALITY_ID)).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));
		final var problems = new Preflight(withHeader, supportManagement, sourceDatabases).check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems();
		assertThat(problems).containsExactly(
			"AD-kontot som tillståndens beslut skrivs med ska bara vara kontots namn, utan blanksteg och semikolon (aot-loader.decision-ad-account, beslut 70)",
			"Beslutsutfallen i SupportManagement gick inte att hämta: 502 BAD_GATEWAY Fel");
	}

	@Test
	void onlyTheMunicipalityOfTheRunIsChecked() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(false)).thenReturn(List.of());
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement, sourceDatabases);

		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems())
			.containsExactly("Etiketten TAGROOT/MIGRATED_ERRAND saknar attributet processBlocked=true, som spärrar processer (beslut 17)");
		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems())
			.contains("Etiketten TAGROOT/MIGRATED_ERRAND finns inte i namespace AOT");
		// The places in Timrå get 2262 as a parameter, but their errands are created in 2281 (decision 19)
		verify(supportManagement, never()).getLabelTree("2262");
		verify(supportManagement, never()).getActionConfigs("2262");
	}

	@Test
	void activeActionsStopARealRun() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		when(supportManagement.getActionConfigs(MUNICIPALITY_ID)).thenReturn(List.of(new Config().name("ADD_LABEL").active(false), new Config().name("SEND_EMAIL").active(true),
			new Config().active(true)));
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement, sourceDatabases);

		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems())
			.containsExactly("Åtgärder är aktiva i namespace AOT och körs när ett migrerat ärende aktiveras: SEND_EMAIL, utan namn. De stängs av under körningen (R6, avsnitt 10.2)");

		when(supportManagement.getActionConfigs(MUNICIPALITY_ID)).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));

		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems())
			.contains("Åtgärderna i SupportManagement gick inte att hämta: 502 BAD_GATEWAY Fel");
	}

	@Test
	void whatIsMissing() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(List.of(new Label().id("x").resourcePath("/TAGROOT/MIGRATED_ERRAND")
			.attributes(List.of(new LabelAttribute().key("processBlocked").value("false")))));
		final var inspection = new ErrandTypeSettings(null, null, null, null, null, List.of("TAGROOT/ALCOHOL_INSPECTION"), null, Map.of("09", ""), true, null, null);
		final var properties = new AotLoaderProperties("AOT", "sent-by", "user", "MIGRATION", " ", "CLOSED", new Roles("PRIMARY", "CONTACT", null, null),
			Map.of(ErrandType.ALCOHOL_INSPECTION, inspection, ErrandType.ALCOHOL_INSPECTION_VISIT,
				new ErrandTypeSettings("INSPECTION", "EXTERNAL_INSPECTION", null, null, null, List.of("CATEGORYROOT/INSPECTION/EXTERNAL_INSPECTION"), null, Map.of(), true, null, null)),
			Map.of(), Map.of(Source.ALKT, new FileAreaDirectories(directory.toString(), directory.resolve("saknas").toString())), DataSize.ofMegabytes(50),
			Map.of("Timrå", "22620"), null);
		final var preflight = new Preflight(properties, supportManagement, sourceDatabases);

		final var result = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN, TILLSYNSBESOK), Source.ALKOL, Set.of(TILLSTAND, TILLSYN)));

		assertThat(result.problems()).contains(
			"Rollen som tillsynsbesökens åtgärder läggs till med är inte angiven (aot-loader.roles.measure-added-by, beslut 57)",
			"Den avslutade statusen för migrerade ärenden är inte angiven (aot-loader.errand-status, beslut 5)",
			"Klassificering saknas för SERVING_PERMIT (aot-loader.errand-types, beslut 5)",
			"Typ i PartyAssets saknas för TOBACCO_PERMIT (aot-loader.errand-types, beslut 20)",
			"Namn i PartyAssets saknas för TOBACCO_PERMIT (aot-loader.errand-types, beslut 4)",
			"Åtgärdstyp för anmärkningarna saknas för ALCOHOL_INSPECTION_VISIT (aot-loader.errand-types, beslut 57)",
			"Etiketten för undertypen PERMANENT_SERVING saknas (aot-loader.errand-types, avsnitt 7.3.4)",
			"Etikett saknas för TOBACCO_PERMIT (aot-loader.errand-types, avsnitt 7.3.4)",
			"Rollen för tillståndshavaren på tillsyns- och dokumentationsärenden är inte angiven (aot-loader.roles.inspection-holder, avsnitt 7.7 och 7.8)",
			"Etiketten för ärendetyp '09' i AlkT är inte bestämd (aot-loader.errand-types, avsnitt 7.7)",
			"Inga ärendetyper i AlkOL är angivna som tillsynsärenden (aot-loader.errand-types, avsnitt 7.7)",
			"Källdatabasen för AlkT är inte konfigurerad",
			"Källdatabasen för AlkOL är inte konfigurerad",
			"Filarean för AlkT, bilagor: katalogen finns inte eller går inte att läsa",
			"Filarean för AlkOL, dokument: katalogen är inte konfigurerad",
			"Etiketten TAGROOT/ALCOHOL_INSPECTION finns inte i namespace AOT",
			"Etiketten TAGROOT/MIGRATED_ERRAND saknar attributet processBlocked=true, som spärrar processer (beslut 17)",
			"Kommunkoden '22620' för området 'timrå' är inte fyra siffror (aot-loader.municipalities, beslut 19)");
		assertThat(result.problems()).filteredOn(problem -> problem.startsWith("Etiketten för undertypen")).hasSize(ServingPermitSubtype.values().length);
		// The holder of a documentation errand gets the same role as that of an inspection errand
		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(DOKUMENTATION))).problems())
			.contains("Rollen för tillståndshavaren på tillsyns- och dokumentationsärenden är inte angiven (aot-loader.roles.inspection-holder, avsnitt 7.7 och 7.8)");
	}

	@Test
	void theLabelsOfTheApplicationAreCompleteAndInTheLabelTree() throws IOException {
		final var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
		final var properties = new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources))
			.bind("aot-loader", AotLoaderProperties.class).get();
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		final var preflight = new Preflight(properties, supportManagement, sourceDatabases);

		final var result = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN, TILLSYNSBESOK), Source.ALKOL, Set.of(TILLSTAND, ANMALAN, TILLSYN, TILLSYNSBESOK)));

		// The settings that wait for decisions are blank in application.yml; the labels and the inspection selection are not
		assertThat(result.problems()).filteredOn(problem -> problem.contains("tikett") || problem.contains("som tillsynsärenden")).isEmpty();
		assertThat(properties.errandType(ErrandType.SERVING_PERMIT).subtypeLabel(ServingPermitSubtype.TASTING)).contains("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/TASTING");
		assertThat(properties.errandType(ErrandType.TOBACCO_INSPECTION).sourceErrandTypes()).containsOnlyKeys("it", "tills");
		assertThat(properties.errandType(ErrandType.TOBACCO_INSPECTION).onlyPlacesWithPermit()).isTrue();
		assertThat(properties.errandType(ErrandType.ALCOHOL_INSPECTION_VISIT).labels()).containsExactly(TestData.EXTERNAL_INSPECTION, "TAGROOT/ALCOHOL_INSPECTION");
		assertThat(properties.errandType(ErrandType.TOBACCO_INSPECTION_VISIT).onlyPlacesWithPermit()).isTrue();
		// The remarks of every visit are deficiencies for the holder to put right (decision 57)
		assertThat(Stream.of(ErrandType.ALCOHOL_INSPECTION_VISIT, ErrandType.TOBACCO_INSPECTION_VISIT).map(type -> properties.errandType(type).measureType())).containsOnly("EXTERNAL");
		// The holder must not see an inspection errand on Mina sidor (decision 27)
		assertThat(properties.roles().inspectionHolder()).isEqualTo("CONTACT").isNotEqualTo(properties.roles().permitHolder());
	}

	@Test
	void labelsThatCannotBeRead() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement, sourceDatabases);

		final var result = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND)));

		assertThat(result.problems()).containsExactly("Etiketterna i SupportManagement gick inte att hämta: 502 BAD_GATEWAY Fel");
		assertThat(result.labelIds().get(MUNICIPALITY_ID)).isEmpty();
	}

	@Test
	void errandTypes() {
		assertThat(Preflight.errandTypes(Map.of(Source.ALKT, Set.of(TILLSTAND)))).containsExactly(ErrandType.SERVING_PERMIT);
		assertThat(Preflight.errandTypes(Map.of(Source.ALKOL, Set.of(ANMALAN)))).hasSize(4);
		assertThat(Preflight.errandTypes(new EnumMap<Source, Set<Kind>>(Source.class))).isEmpty();
	}

	/**
	 * A label tree with every label a run can use, nested as SupportManagement nests them.
	 */
	static List<Label> labelTree(final boolean processBlocked) {
		final var paths = new ArrayList<String>(List.of(
			"TAGROOT/MIGRATED_ERRAND", "TAGROOT/ALCOHOL_INSPECTION", "TAGROOT/TOBACCO_INSPECTION",
			"CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION", "CATEGORYROOT/TOBACCO/SALES_PERMIT_APPLICATION", "CATEGORYROOT/ALCOHOL/FOLKOL_SALES_NOTIFICATION", "CATEGORYROOT/ALCOHOL/FOLKOL_SERVING_NOTIFICATION",
			"CATEGORYROOT/TOBACCO/ECIGARETTE_SALES_NOTIFICATION", "CATEGORYROOT/TOBACCO/TOBACCO_FREE_NICOTINE_SALES_NOTIFICATION",
			"CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION", "CATEGORYROOT/INSPECTION/EXTERNAL_INSPECTION"));
		Arrays.stream(ServingPermitSubtype.values()).forEach(subtype -> paths.add("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/" + subtype.name()));
		// The categories and types of the classifications of the settings
		Arrays.stream(ErrandType.values()).forEach(type -> paths.add("CATEGORYROOT/CATEGORY_" + type + "/TYPE_" + type));
		paths.add(TestData.DOCUMENTATION);
		final var children = paths.stream()
			.map(path -> new Label().id("id-" + path).resourcePath("/" + path)
				.attributes("TAGROOT/MIGRATED_ERRAND".equals(path) ? List.of(new LabelAttribute().key(" ProcessBlocked ").value(processBlocked ? "TRUE" : "false")) : null))
			.toList();
		return List.of(new Label().id("root").resourcePath(null).labels(children));
	}
}
