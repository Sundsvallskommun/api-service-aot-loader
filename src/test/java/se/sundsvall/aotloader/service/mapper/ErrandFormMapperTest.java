package se.sundsvall.aotloader.service.mapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.FileReference.FileFolder;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.Invoicing;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.JsonContent;
import se.sundsvall.aotloader.service.model.JsonParameterType;
import se.sundsvall.aotloader.service.model.Place;
import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.source.FilePurposes.BUSINESS_DESCRIPTION;
import static se.sundsvall.aotloader.source.FilePurposes.POWER_OF_ATTORNEY;
import static se.sundsvall.aotloader.source.FilePurposes.SELF_MONITORING_PROGRAM;

class ErrandFormMapperTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final Holder HOLDER = new Holder(TestData.HOLDER_ID, "Bolaget AB", "Storgatan 1", "85230", "Sundsvall", null, null, null);
	private static final LocalDate NOTIFIED = LocalDate.of(2019, 5, 1);

	private final ErrandFormMapper mapper = new ErrandFormMapper(TestData.properties());

	@ParameterizedTest
	@EnumSource(ErrandType.class)
	void anErrandGetsTheFormOfTheTypeLabelOfItsSettings(final ErrandType type) {
		final var candidate = candidate(type, HOLDER, new Place("Kiosken", "Torget 2", "85231", "Sundsvall", "060-1", null, null, null), Map.of());

		assertThat(mapper.toForm(candidate, MUNICIPALITY_ID).map(JsonContent::type))
			.isEqualTo(ErrandFormMapper.formsOf(TestData.properties().errandType(type).allLabels()).stream().findFirst());
	}

	@Test
	void aNotificationOfFolkolSalesGetsWhatTheSourceGivesAndTheAnswersDecidedForWhatItDoesNot() {
		// The address of the place is the one of the company, written otherwise
		final var candidate = notification(ErrandType.FOLKOL_SALES_NOTIFICATION,
			new Place("Kiosken", "STORGATAN 1", "852 30", "sundsvall", "060-12 34 56", "kiosk@example.com", null, null), Map.of());

		final var form = mapper.toForm(candidate, MUNICIPALITY_ID);

		// Permanent sales of food, without which the schema stops a notification, documented at inspection (decision 107)
		assertThat(form).hasValueSatisfying(json -> {
			assertThat(json.type()).isEqualTo(JsonParameterType.FOLKOL_SALES_NOTIFICATION_FORM);
			assertThat(json.value()).hasToString("""
				{"isAuthorizedSignatory":"YES","invoiceAddressSameAsCompany":"YES","businessDescriptionChoice":"DESCRIBE",\
				"businessDescription":"Uppgift saknas – migrerat ärende","hasPermanentFoodSales":"YES","producesLowAlcoholBeerOnPremises":"NO",\
				"canShowDocumentationAtInspection":["CAN_SHOW_DOCUMENTATION"],"selfMonitoringProgram":"NO_CAN_SHOW_AT_INSPECTION","premisesName":"Kiosken",\
				"premisesAddressSameAsCompany":"YES","premisesContact":{"phoneNumber":"060-12 34 56","emailAddress":"kiosk@example.com"}}""");
		});
		assertThat(ErrandFormMapper.reportLines(candidate)).isEmpty();
	}

	@Test
	void theFilesOfTheErrandAnswerTheQuestionsOfAttachmentsAndTheInvoicesGoWhereThePlaceSays() {
		final var invoicing = new Invoicing("Ekonomibyrån AB", "Box 12", "852 99", "Sundsvall", "Kalle Kassör");
		final var candidate = notification(ErrandType.FOLKOL_SERVING_NOTIFICATION,
			new Place("Krogen", "Torget 1", "85231", "Sundsvall", null, null, null, null, invoicing),
			Map.of(file("fullmakt.pdf"), new FileDetails(POWER_OF_ATTORNEY, null), file("verksamhet.pdf"), new FileDetails(BUSINESS_DESCRIPTION, null),
				file("egenkontroll.pdf"), new FileDetails(SELF_MONITORING_PROGRAM, null)));

		// A registration as a food business, without which the schema stops a notification of serving (decision 107)
		assertThat(mapper.toForm(candidate, MUNICIPALITY_ID)).map(JsonContent::value).hasValueSatisfying(value -> assertThat(value).hasToString("""
			{"isAuthorizedSignatory":"NO","invoiceAddressSameAsCompany":"NO",\
			"invoiceRecipient":{"careOf":"Ekonomibyrån AB","street":"Box 12","postalCode":"85299","city":"Sundsvall"},"invoiceReference":"Kalle Kassör",\
			"businessDescriptionChoice":"UPLOAD","isFoodRegistered":"YES","canShowFoodRegistrationAtInspection":["CAN_SHOW_FOOD_REGISTRATION"],\
			"selfMonitoringProgram":"YES","premisesName":"Krogen","premisesAddressSameAsCompany":"NO",\
			"premisesAddress":{"street":"Torget 1","postalCode":"85231","city":"Sundsvall"}}"""));
	}

	@Test
	void theInvoicesGoToTheCompanyWhenThePlaceHasNoOtherInvoiceAddress() {
		final var sameAddress = form(kiosk(new Invoicing("BOLAGET AB", "Storgatan 1", "852 30", "Sundsvall", null)), HOLDER);
		final var onlyAReference = form(kiosk(new Invoicing(null, null, null, null, "Kalle Kassör")), HOLDER);
		final var none = form(kiosk(null), HOLDER);

		assertThat(List.of(sameAddress, onlyAReference, none)).allSatisfy(value -> {
			assertThat(value.get("invoiceAddressSameAsCompany").asString()).isEqualTo("YES");
			assertThat(value.has("invoiceRecipient")).isFalse();
		});
		assertThat(onlyAReference.get("invoiceReference").asString()).isEqualTo("Kalle Kassör");
		assertThat(sameAddress.has("invoiceReference")).isFalse();
	}

	@Test
	void theCompanyAsTheRecipientOfInvoicesToAnotherAddressIsNoCareOf() {
		final var company = form(kiosk(new Invoicing("Bolaget AB", "Box 5", "85231", "Sundsvall", null)), HOLDER);
		final var nameless = form(kiosk(new Invoicing(" ", "Box 5", "85231", "Sundsvall", null)), HOLDER);

		assertThat(List.of(company, nameless)).allSatisfy(value -> {
			assertThat(value.get("invoiceAddressSameAsCompany").asString()).isEqualTo("NO");
			assertThat(value.get("invoiceRecipient")).hasToString("""
				{"street":"Box 5","postalCode":"85231","city":"Sundsvall"}""");
		});
	}

	@Test
	void withoutAHolderToCompareWithTheAddressesOfThePlaceAndOfTheInvoicesAreGiven() {
		final var value = form(kiosk(new Invoicing("Bolaget AB", "Storgatan 1", "85230", "Sundsvall", null)), null);

		assertThat(value.get("invoiceAddressSameAsCompany").asString()).isEqualTo("NO");
		assertThat(value.get("invoiceRecipient")).hasToString("""
			{"careOf":"Bolaget AB","street":"Storgatan 1","postalCode":"85230","city":"Sundsvall"}""");
		assertThat(value.get("premisesAddressSameAsCompany").asString()).isEqualTo("NO");
		assertThat(value.get("premisesAddress")).hasToString("""
			{"street":"Storgatan 1","postalCode":"85230","city":"Sundsvall"}""");
	}

	@Test
	void addressesThatDifferOnlyInBlanksOrInHowALetterIsComposedAreTheSame() {
		final var holder = new Holder(TestData.HOLDER_ID, "Bolaget AB", "Östra vägen 1", "85230", "Sundsvall", null, null, null);
		// A no-break space, and Ö as O with a combining diaeresis
		final var place = new Place("Kiosken", "Östra vägen 1", "852 30", "Sundsvall", null, null, null, null);

		final var value = mapper.toForm(candidate(ErrandType.FOLKOL_SALES_NOTIFICATION, holder, place, Map.of()), MUNICIPALITY_ID).orElseThrow().value();

		assertThat(value.get("premisesAddressSameAsCompany").asString()).isEqualTo("YES");
		assertThat(value.has("premisesAddress")).isFalse();
	}

	@Test
	void anInvoiceAddressThatCannotBeGivenLeavesTheErrandWithoutErrandData() {
		final var withoutCity = notification(ErrandType.FOLKOL_SALES_NOTIFICATION, kiosk(new Invoicing("Ekonomibyrån AB", "Box 12", "85299", null, null)), Map.of());
		final var badPostalCode = notification(ErrandType.FOLKOL_SALES_NOTIFICATION, kiosk(new Invoicing(null, "Box 12", "S-852", "Sundsvall", null)), Map.of());

		assertThat(mapper.toForm(withoutCity, MUNICIPALITY_ID)).isEmpty();
		assertThat(ErrandFormMapper.reportLines(withoutCity))
			.containsExactly("Inga ärendeuppgifter, eftersom formuläret kräver det som saknas: fakturaadressen saknar gata, postnummer eller ort");
		assertThat(ErrandFormMapper.reportLines(badPostalCode))
			.containsExactly("Inga ärendeuppgifter, eftersom formuläret kräver det som saknas: fakturaadressen har ett postnummer utan fem siffror");
	}

	@Test
	void aNotificationOfECigarettesIsSoldUntilFurtherNoticeFromTheDayOfTheNotification() {
		final var candidate = notification(ErrandType.ECIGARETTE_NOTIFICATION, new Place("Kiosken", "Torget 2", "85231", "Sundsvall", "060-1", null, null, null), Map.of());

		assertThat(mapper.toForm(candidate, MUNICIPALITY_ID)).hasValueSatisfying(json -> {
			assertThat(json.type()).isEqualTo(JsonParameterType.ECIGARETTE_NOTIFICATION_FORM);
			assertThat(json.value()).hasToString("""
				{"isAuthorizedSignatory":"YES","invoiceAddressSameAsCompany":"YES","businessDescriptionChoice":"DESCRIBE",\
				"businessDescription":"Uppgift saknas – migrerat ärende","salesPeriod":"INDEFINITE","salesStartDate":"2019-05-01","pointOfSaleName":"Kiosken",\
				"pointOfSaleAddressSameAsCompany":"NO","pointOfSaleAddress":{"street":"Torget 2","postalCode":"85231","city":"Sundsvall"},\
				"pointOfSaleContact":{"phoneNumber":"060-1"},"includesDistanceSelling":"NO","includesParticularlyDangerousProducts":"NO",\
				"hasSupplierAgreement":"NO"}""");
		});
	}

	@Test
	void aNotificationOfTobaccoFreeNicotineProductsHasNoQuestionOfParticularlyDangerousProducts() {
		final var candidate = notification(ErrandType.TOBACCO_FREE_NICOTINE_NOTIFICATION, new Place("Kiosken", "Storgatan 1", "85230", "Sundsvall", "060-1", null, null, null),
			Map.of());

		assertThat(mapper.toForm(candidate, MUNICIPALITY_ID)).hasValueSatisfying(json -> {
			assertThat(json.type()).isEqualTo(JsonParameterType.TOBACCO_FREE_NICOTINE_NOTIFICATION_FORM);
			assertThat(json.value().has("includesParticularlyDangerousProducts")).isFalse();
			assertThat(json.value().get("pointOfSaleAddressSameAsCompany").asString()).isEqualTo("YES");
			assertThat(json.value().has("pointOfSaleAddress")).isFalse();
			assertThat(json.value().get("hasSupplierAgreement").asString()).isEqualTo("NO");
		});
	}

	@Test
	void whatTheFormRequiresAndTheSourceLacksIsToldTogetherWithWhatIsLeftOut() {
		final var candidate = Candidate.builder(new ItemKey(ErrandType.ECIGARETTE_NOTIFICATION.getSource(), ErrandType.ECIGARETTE_NOTIFICATION.getKind(), "1-ECIG"),
			ErrandType.ECIGARETTE_NOTIFICATION)
			.labelPaths(labelsOf(ErrandType.ECIGARETTE_NOTIFICATION))
			.holder(HOLDER)
			.place(new Place(null, "Torget 2", "852", "Sundsvall", " ", "kiosken på torget", null, null))
			.build();

		assertThat(mapper.toForm(candidate, MUNICIPALITY_ID)).isEmpty();
		assertThat(ErrandFormMapper.reportLines(candidate)).containsExactly(
			"Inga ärendeuppgifter, eftersom formuläret kräver det som saknas: anmälan saknar datum; stället saknar namn; ställets adress har ett postnummer utan fem siffror; "
				+ "stället saknar telefon",
			"Utelämnat i ärendeuppgifterna, eftersom det inte följer schemat: pointOfSaleContact.emailAddress: har inte formen av en e-postadress");
	}

	@Test
	void aNotificationWithoutAPlaceGetsNoErrandData() {
		final var withoutPlace = notification(ErrandType.FOLKOL_SALES_NOTIFICATION, null, Map.of());

		assertThat(mapper.toForm(withoutPlace, MUNICIPALITY_ID)).isEmpty();
		assertThat(ErrandFormMapper.reportLines(withoutPlace)).containsExactly("Inga ärendeuppgifter, eftersom formuläret kräver det som saknas: ärendet saknar ställe");
	}

	@Test
	void anErrandWhoseTypeHasNoFormTheMigrationFillsGetsNone() {
		final var permit = TestData.permit("1");

		assertThat(mapper.toForm(permit, MUNICIPALITY_ID)).isEmpty();
		assertThat(ErrandFormMapper.reportLines(permit)).isEmpty();
	}

	@Test
	void anInspectionErrandNamesItsServingPlaceWithTheMunicipality() {
		final var candidate = candidate(ErrandType.ALCOHOL_INSPECTION, null,
			new Place("Restaurang 1", "Storgatan 1", "852 30", "Timrå", "060-12 34 56", "info@example.com", " timrå ", "12345678"), Map.of());

		assertThat(mapper.toForm(candidate, MUNICIPALITY_ID)).hasValueSatisfying(form -> {
			assertThat(form.type()).isEqualTo(JsonParameterType.INTERNAL_INSPECTION_FORM);
			assertThat(form.value()).hasToString("""
				{"premises":{"name":"Restaurang 1","address":{"street":"Storgatan 1","postalCode":"85230","city":"Timrå"},"phoneNumber":"060-12 34 56",\
				"emailAddress":"info@example.com","restaurantNumber":"12345678","municipalityId":"2262"}}""");
		});
		assertThat(ErrandFormMapper.reportLines(candidate)).isEmpty();
	}

	@Test
	void aVisitToASalesPlaceNamesItAsThePointOfSaleWithoutWhatDoesNotFollowTheSchema() {
		final var candidate = candidate(ErrandType.TOBACCO_INSPECTION_VISIT, null,
			new Place("Kiosken", "Torget 2", "852 31", "Sundsvall", null, "kiosken på torget", null, "12345678"), Map.of());

		// A sales place has no restaurant number in the schema
		assertThat(mapper.toForm(candidate, MUNICIPALITY_ID)).map(JsonContent::value).hasValueSatisfying(value -> assertThat(value).hasToString("""
			{"pointOfSale":{"name":"Kiosken","address":{"street":"Torget 2","postalCode":"85231","city":"Sundsvall"},"municipalityId":"2281"}}"""));
		assertThat(ErrandFormMapper.reportLines(candidate))
			.containsExactly("Utelämnat i ärendeuppgifterna, eftersom det inte följer schemat: pointOfSale.emailAddress: har inte formen av en e-postadress");
	}

	@Test
	void aPlaceWithoutAnythingTheFormTakesIsStillNamedByItsMunicipality() {
		final var candidate = candidate(ErrandType.ALCOHOL_INSPECTION_VISIT, null, new Place(null, null, "8523", null, null, null, null, "1234"), Map.of());

		assertThat(mapper.toForm(candidate, MUNICIPALITY_ID)).map(JsonContent::value).hasValueSatisfying(value -> assertThat(value).hasToString("""
			{"premises":{"municipalityId":"2281"}}"""));
		assertThat(ErrandFormMapper.reportLines(candidate)).containsExactly(
			"Utelämnat i ärendeuppgifterna, eftersom det inte följer schemat: premises.address.postalCode: har inte fem siffror; premises.restaurantNumber: har inte åtta siffror");
	}

	@Test
	void anInspectionErrandWithoutAPlaceGetsNoErrandDataAndNothingToReport() {
		final var candidate = candidate(ErrandType.ALCOHOL_INSPECTION, null, null, Map.of());

		assertThat(mapper.toForm(candidate, MUNICIPALITY_ID)).isEmpty();
		assertThat(ErrandFormMapper.reportLines(candidate)).isEmpty();
	}

	@Test
	void aNotificationGetsNoMunicipality() {
		final var value = form(new Place("Kiosken", "Storgatan 1", "85230", "Sundsvall", null, null, " Timrå ", null), HOLDER);

		assertThat(value.has("municipalityId")).isFalse();
		assertThat(value.has("premises")).isFalse();
	}

	/**
	 * @return the form of a notification of low-alcohol beer sales at the place
	 */
	private JsonNode form(final Place place, final Holder holder) {
		return mapper.toForm(candidate(ErrandType.FOLKOL_SALES_NOTIFICATION, holder, place, Map.of()), MUNICIPALITY_ID).orElseThrow().value();
	}

	private static Place kiosk(final Invoicing invoicing) {
		return new Place("Kiosken", "Storgatan 1", "85230", "Sundsvall", null, null, null, null, invoicing);
	}

	private static Candidate notification(final ErrandType type, final Place place, final Map<FileReference, FileDetails> files) {
		return candidate(type, HOLDER, place, files);
	}

	/**
	 * @return the candidate with the labels of the settings of its type, as the sources give them, and for a type with a
	 *         post a post notified on {@link #NOTIFIED}
	 */
	private static Candidate candidate(final ErrandType type, final Holder holder, final Place place, final Map<FileReference, FileDetails> files) {
		final var builder = Candidate.builder(new ItemKey(type.getSource(), type.getKind(), "1-" + type.name()), type)
			.labelPaths(labelsOf(type))
			.holder(holder)
			.place(place)
			.files(List.copyOf(files.keySet()), List.of(), files);
		if (type.isWithAsset()) {
			builder.asset(new AssetData(null, "Anmälan", NOTIFIED, null, type.getAssetSchema(), null, List.of(), null, List.of()));
		}
		return builder.build();
	}

	private static List<String> labelsOf(final ErrandType type) {
		return List.copyOf(TestData.properties().errandType(type).allLabels());
	}

	private static FileReference file(final String name) {
		return new FileReference(name, FileFolder.ATTACHMENTS);
	}
}
