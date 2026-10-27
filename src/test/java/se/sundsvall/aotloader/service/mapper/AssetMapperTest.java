package se.sundsvall.aotloader.service.mapper;

import generated.se.sundsvall.partyassets.Asset;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import generated.se.sundsvall.partyassets.AssetUpdateRequest;
import generated.se.sundsvall.partyassets.Status;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.MigrationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;

class AssetMapperTest {

	private static final String ERRAND_ID = "11111111-1111-1111-1111-111111111111";
	private static final String MUNICIPALITY_ID = "2281";

	private final AssetMapper mapper = new AssetMapper(TestData.properties());

	@Test
	void theLegalBasisKeepsTheReferencesThatFitWhole() {
		final var first = "8 kap. 2 § alkohollagen (2010:1622)";
		final var many = String.join("; ", Collections.nCopies(10, first));

		assertThat(AssetMapper.legalBasisWithin(null)).isNull();
		assertThat(AssetMapper.legalBasisWithin(first)).isEqualTo(first);
		assertThat(AssetMapper.legalBasisWithin(many)).doesNotContain("se ärendet").isEqualTo(String.join("; ", Collections.nCopies(6, first)));
		assertThat(AssetMapper.legalBasisWithin("x".repeat(300))).hasSize(255).endsWith("…");
	}

	@Test
	void theParametersOfThePostThatPartyAssetsTakesOnlyCutAreTold() {
		final var asset = new AssetData(null, "Serveringstillstånd", null, null, Map.of("conditions", "x".repeat(256), "omrade", "x".repeat(255)));

		assertThat(AssetMapper.truncatedParameters(asset)).containsExactly("conditions");
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
	void aPlaceInAnAreaOfAnotherMunicipalityHasThatMunicipalityOnThePost() {
		final var asset = mapper.toAsset(TestData.permit("1", "timrå"), MUNICIPALITY_ID, ERRAND_ID, "party-id", false);

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
	void sourceReference() {
		assertThat(mapper.toSourceReference(ERRAND_ID)).isEqualTo("LINK|" + ERRAND_ID + ";case;supportmanagement;AOT|");
	}
}
