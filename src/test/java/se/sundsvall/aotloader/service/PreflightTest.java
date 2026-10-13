package se.sundsvall.aotloader.service;

import generated.se.sundsvall.supportmanagement.Config;
import generated.se.sundsvall.supportmanagement.Label;
import generated.se.sundsvall.supportmanagement.LabelAttribute;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.springframework.util.unit.DataSize;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.ErrandTypeSettings;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.FileAreaDirectories;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Roles;
import se.sundsvall.aotloader.integration.supportmanagement.SupportManagementIntegration;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.ServingPermitSubtype;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;

@ExtendWith(MockitoExtension.class)
class PreflightTest {

	private static final String MUNICIPALITY_ID = "2281";

	@Mock
	private SupportManagementIntegration supportManagement;

	@TempDir
	private Path directory;

	@Test
	void everythingInPlace() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		when(supportManagement.getLabelTree("2262")).thenReturn(labelTree(true));
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement);

		final var result = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN), Source.ALKOL, Set.of(TILLSTAND, ANMALAN, TILLSYN)));

		assertThat(result.problems()).isEmpty();
		assertThat(result.labelIds()).containsOnlyKeys(MUNICIPALITY_ID, "2262");
		assertThat(result.labelIds().get(MUNICIPALITY_ID)).containsEntry("TAGROOT/MIGRATED_ERRAND", "id-TAGROOT/MIGRATED_ERRAND")
			.containsEntry("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/TASTING", "id-CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/TASTING");
	}

	@Test
	void theLabelsOfAnotherMunicipalityAreCheckedToo() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		when(supportManagement.getLabelTree("2262")).thenReturn(labelTree(false)).thenReturn(List.of());
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement);

		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems())
			.containsExactly("Etiketten TAGROOT/MIGRATED_ERRAND för kommun 2262 saknar attributet processBlocked=true, som spärrar processer (beslut 17)");
		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems())
			.contains("Etiketten TAGROOT/MIGRATED_ERRAND finns inte i namespace AOT för kommun 2262");

		when(supportManagement.getLabelTree("2262")).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));

		final var failed = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND)));

		assertThat(failed.problems()).containsExactly("Etiketterna i SupportManagement för kommun 2262 gick inte att hämta: 502 BAD_GATEWAY Fel");
		assertThat(failed.labelIds().get("2262")).isEmpty();
	}

	@Test
	void activeActionsInAnyMunicipalityStopARealRun() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		when(supportManagement.getLabelTree("2262")).thenReturn(labelTree(true));
		when(supportManagement.getActionConfigs(MUNICIPALITY_ID)).thenReturn(List.of(new Config().name("ADD_LABEL").active(false), new Config().name("SEND_EMAIL")));
		when(supportManagement.getActionConfigs("2262")).thenReturn(List.of(new Config().name("SEND_EMAIL").active(true), new Config().active(true)));
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement);

		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems())
			.containsExactly("Åtgärder är aktiva i namespace AOT för kommun 2262 och körs när ett migrerat ärende aktiveras: SEND_EMAIL, utan namn. De stängs av under körningen (R6, avsnitt 10.2)");

		when(supportManagement.getActionConfigs(MUNICIPALITY_ID)).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));

		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems())
			.contains("Åtgärderna i SupportManagement gick inte att hämta: 502 BAD_GATEWAY Fel");
	}

	@Test
	void whatIsMissing() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(List.of(new Label().id("x").resourcePath("/TAGROOT/MIGRATED_ERRAND")
			.attributes(List.of(new LabelAttribute().key("processBlocked").value("false")))));
		final var inspection = new ErrandTypeSettings(null, null, null, null, List.of("TAGROOT/ALCOHOL_INSPECTION"), null, Map.of("09", ""), true);
		final var properties = new AotLoaderProperties("AOT", "sent-by", "user", "MIGRATION", " ", new Roles("PRIMARY", "CONTACT", null),
			Map.of(ErrandType.ALCOHOL_INSPECTION, inspection), Map.of(), Map.of(Source.ALKT, new FileAreaDirectories(directory.toString(), directory.resolve("saknas").toString())), DataSize.ofMegabytes(50),
			Map.of("Timrå", "22620"));
		final var preflight = new Preflight(properties, supportManagement);

		final var result = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN), Source.ALKOL, Set.of(TILLSTAND, TILLSYN)));

		assertThat(result.problems()).contains(
			"Den avslutade statusen för migrerade ärenden är inte angiven (aot-loader.errand-status, beslut 5)",
			"Klassificering saknas för SERVING_PERMIT (aot-loader.errand-types, beslut 5)",
			"Typ i PartyAssets saknas för TOBACCO_PERMIT (aot-loader.errand-types, beslut 20)",
			"Namn i PartyAssets saknas för TOBACCO_PERMIT (aot-loader.errand-types, beslut 4)",
			"Etiketten för undertypen PERMANENT_SERVING saknas (aot-loader.errand-types, avsnitt 7.3.4)",
			"Etikett saknas för TOBACCO_PERMIT (aot-loader.errand-types, avsnitt 7.3.4)",
			"Rollen för tillståndshavaren på tillsynsärenden är inte angiven (aot-loader.roles.inspection-holder, avsnitt 7.7)",
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
	}

	@Test
	void theLabelsOfTheApplicationAreCompleteAndInTheLabelTree() throws IOException {
		final var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
		final var properties = new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources))
			.bind("aot-loader", AotLoaderProperties.class).get();
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(labelTree(true));
		when(supportManagement.getLabelTree("2262")).thenReturn(labelTree(true));
		final var preflight = new Preflight(properties, supportManagement);

		final var result = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN), Source.ALKOL, Set.of(TILLSTAND, ANMALAN, TILLSYN)));

		// The settings that wait for decisions are blank in application.yml; the labels and the inspection selection are not
		assertThat(result.problems()).filteredOn(problem -> problem.contains("tikett") || problem.contains("som tillsynsärenden")).isEmpty();
		assertThat(properties.errandType(ErrandType.SERVING_PERMIT).subtypeLabel(ServingPermitSubtype.TASTING)).contains("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/TASTING");
		assertThat(properties.errandType(ErrandType.TOBACCO_INSPECTION).sourceErrandTypes()).containsOnlyKeys("it", "tills");
		assertThat(properties.errandType(ErrandType.TOBACCO_INSPECTION).onlyPlacesWithPermit()).isTrue();
	}

	@Test
	void labelsThatCannotBeRead() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));
		when(supportManagement.getLabelTree("2262")).thenReturn(labelTree(true));
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement);

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
			"CATEGORYROOT/TOBACCO/SALES_PERMIT_APPLICATION", "CATEGORYROOT/ALCOHOL/FOLKOL_SALES_NOTIFICATION", "CATEGORYROOT/ALCOHOL/FOLKOL_SERVING_NOTIFICATION",
			"CATEGORYROOT/TOBACCO/ECIGARETTE_SALES_NOTIFICATION", "CATEGORYROOT/TOBACCO/TOBACCO_FREE_NICOTINE_SALES_NOTIFICATION",
			"CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION", "CATEGORYROOT/INSPECTION/EXTERNAL_INSPECTION"));
		Arrays.stream(ServingPermitSubtype.values()).forEach(subtype -> paths.add("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/" + subtype.name()));
		final var children = paths.stream()
			.map(path -> new Label().id("id-" + path).resourcePath("/" + path)
				.attributes("TAGROOT/MIGRATED_ERRAND".equals(path) ? List.of(new LabelAttribute().key(" ProcessBlocked ").value(processBlocked ? "TRUE" : "false")) : null))
			.toList();
		return List.of(new Label().id("root").resourcePath(null).labels(children));
	}
}
