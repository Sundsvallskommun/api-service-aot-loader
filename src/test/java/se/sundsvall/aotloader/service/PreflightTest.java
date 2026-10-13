package se.sundsvall.aotloader.service;

import generated.se.sundsvall.supportmanagement.Label;
import generated.se.sundsvall.supportmanagement.LabelAttribute;
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
import org.springframework.util.unit.DataSize;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.FileAreaDirectories;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Inspection;
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
		when(supportManagement.getLabelTree("2262")).thenReturn(labelTree(false), List.of());
		final var preflight = new Preflight(TestData.properties(directory.toString(), directory.toString()), supportManagement);

		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems())
			.containsExactly("Etiketten TAGROOT/MIGRATED_ERRAND för kommun 2262 saknar attributet processBlocked=true, som spärrar processer (beslut 17)");
		assertThat(preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND))).problems())
			.contains("Etiketten TAGROOT/MIGRATED_ERRAND finns inte i namespace ALKT för kommun 2262");

		when(supportManagement.getLabelTree("2262")).thenThrow(Problem.valueOf(BAD_GATEWAY, "Fel"));

		final var failed = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND)));

		assertThat(failed.problems()).containsExactly("Etiketterna i SupportManagement för kommun 2262 gick inte att hämta: 502 BAD_GATEWAY Fel");
		assertThat(failed.labelIds().get("2262")).isEmpty();
	}

	@Test
	void whatIsMissing() {
		when(supportManagement.getLabelTree(MUNICIPALITY_ID)).thenReturn(List.of(new Label().id("x").resourcePath("/TAGROOT/MIGRATED_ERRAND")
			.attributes(List.of(new LabelAttribute().key("processBlocked").value("false")))));
		final var properties = new AotLoaderProperties("ALKT", "sent-by", "user", "MIGRATION", " ", new Roles("PRIMARY", "CONTACT", null),
			Map.of(), new Inspection(true, Map.of("09", ""), Map.of()), Map.of(), Map.of(Source.ALKT, new FileAreaDirectories(directory.toString(), directory.resolve("saknas").toString())), DataSize.ofMegabytes(50), Map.of("Timrå", "22620"));
		final var preflight = new Preflight(properties, supportManagement);

		final var result = preflight.check(MUNICIPALITY_ID, Map.of(Source.ALKT, Set.of(TILLSTAND, TILLSYN), Source.ALKOL, Set.of(TILLSTAND)));

		assertThat(result.problems()).contains(
			"Den avslutade statusen för migrerade ärenden är inte angiven (aot-loader.errand-status, beslut 5)",
			"Klassificering saknas för SERVING_PERMIT (aot-loader.errand-types, beslut 5)",
			"Typ i PartyAssets saknas för TOBACCO_PERMIT (aot-loader.errand-types, beslut 20)",
			"Namn i PartyAssets saknas för TOBACCO_PERMIT (aot-loader.errand-types, beslut 4)",
			"Rollen för tillståndshavaren på tillsynsärenden är inte angiven (aot-loader.roles.inspection-holder, avsnitt 7.7)",
			"Etiketten för ärendetyp '09' i AlkT är inte bestämd (aot-loader.inspection, avsnitt 7.7)",
			"Källdatabasen för AlkT är inte konfigurerad",
			"Källdatabasen för AlkOL är inte konfigurerad",
			"Filarean för AlkT, bilagor: katalogen finns inte eller går inte att läsa",
			"Filarean för AlkOL, dokument: katalogen är inte konfigurerad",
			"Etiketten CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/PERMANENT_SERVING finns inte i namespace ALKT",
			"Etiketten TAGROOT/MIGRATED_ERRAND saknar attributet processBlocked=true, som spärrar processer (beslut 17)",
			"Kommunkoden '22620' för området 'timrå' är inte fyra siffror (aot-loader.municipalities, beslut 19)");
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
		Arrays.stream(ServingPermitSubtype.values()).forEach(subtype -> paths.add(subtype.getLabelPath()));
		final var children = paths.stream()
			.map(path -> new Label().id("id-" + path).resourcePath("/" + path)
				.attributes("TAGROOT/MIGRATED_ERRAND".equals(path) ? List.of(new LabelAttribute().key(" ProcessBlocked ").value(processBlocked ? "TRUE" : "false")) : null))
			.toList();
		return List.of(new Label().id("root").resourcePath(null).labels(children));
	}
}
