package se.sundsvall.aotloader.source.alkol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReaderResult.Notice;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.AssetParameters;
import se.sundsvall.aotloader.source.CandidateTexts;
import se.sundsvall.aotloader.source.CodeTexts;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.FileList;
import se.sundsvall.aotloader.source.InspectionCandidates;
import se.sundsvall.aotloader.source.InspectionCandidates.InspectionErrand;
import se.sundsvall.aotloader.source.InspectionVisitCandidates;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.source.ParameterList;
import se.sundsvall.aotloader.source.PartyRows;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.EventRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.PermitViewRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.SalesPlaceRow;
import se.sundsvall.aotloader.source.files.SharedFileNames;
import se.sundsvall.aotloader.support.DiaryNumbers;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.TAG_DIARY_NUMBER;
import static se.sundsvall.aotloader.Constants.TAG_NOTIFICATION;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.ReviewRule.APPLICANT_NOT_OWNER;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_ON_SEVERAL;
import static se.sundsvall.aotloader.service.model.ReviewRule.ISSUE_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_INVALID;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_ON_SEVERAL_OWNERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_ONLY_FLAG;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_COPY_DIFFERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERMIT_ENDED;
import static se.sundsvall.aotloader.service.model.ReviewRule.PLACE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.SALES_PLACE_CEASED;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNDERLYING_ERRAND_OPEN;
import static se.sundsvall.aotloader.service.model.Source.ALKOL;
import static se.sundsvall.aotloader.source.CandidateTexts.UNKNOWN_SALES_PLACE;
import static se.sundsvall.aotloader.source.CandidateTexts.line;

/**
 * Turns what was read from AlkOL, the database of tobacco, folköl, e-cigarettes and tobacco-free nicotine products,
 * into candidates: the tobacco permits of the view FS_GT_Tobak, one notification per active sales place and product,
 * and the inspection errands and visits.
 */
public final class AlkolCandidateFactory {

	/** The notice for an active sales place without any notified product and without a tobacco permit. */
	static final String NOTICE_WITHOUT_PRODUCT = "Ö1";
	/** The notice for an active sales place marked for tobacco but without a current tobacco permit. */
	static final String NOTICE_TOBACCO_WITHOUT_PERMIT = "Ö2";

	private static final Set<String> GRANTING_DECISIONS = Set.of("GTDE", "GTPH", "GTDI");
	private static final Set<String> ENDING_DECISIONS = Set.of("GTDEU", "GTDEÅ");
	private static final String SERVING_PLACE_CATEGORY_PREFIX = "serveringsställe";
	// The product part of the id of the notice for a sales place without any notified product
	private static final String NO_PRODUCT = "INGEN";
	private static final Comparator<DecisionRow> DECISION_ORDER = Comparator
		.comparing(DecisionRow::beslutad, Comparator.nullsFirst(Comparator.naturalOrder()))
		.thenComparing(DecisionRow::beslutsId, Comparator.nullsFirst(Comparator.naturalOrder()));

	private final AlkolSnapshot snapshot;
	private final LocalDate today;
	private final AotLoaderProperties properties;
	private final Map<Integer, SalesPlaceRow> placesById = new LinkedHashMap<>();
	private final Map<Integer, ErrandRow> errandsById = new HashMap<>();
	private final Map<Integer, List<ErrandRow>> errandsByPlace = new HashMap<>();
	private final Map<Integer, List<DecisionRow>> decisionsByPlace = new HashMap<>();
	private final Map<Integer, List<DecisionRow>> decisionsByErrand = new HashMap<>();
	private final Map<Integer, List<EventRow>> eventsByPlace = new HashMap<>();
	private final Map<Integer, List<EventRow>> eventsByErrand = new HashMap<>();
	private final Map<String, Set<Integer>> ownersByNumber = new HashMap<>();
	private final Map<String, Integer> permitsByDiaryNumber = new HashMap<>();
	private final SharedFileNames sharedFileNames;

	public AlkolCandidateFactory(final AlkolSnapshot snapshot, final LocalDate today, final AotLoaderProperties properties) {
		this.snapshot = snapshot;
		this.today = today;
		this.properties = properties;
		snapshot.places().forEach(place -> placesById.put(place.fsId(), place));
		indexErrands();
		indexDecisions();
		indexEvents();
		indexOwnerNumbers();
		indexDiaryNumbers();
		this.sharedFileNames = sharedFileNames();
	}

	/**
	 * @return the tobacco permits of the sales places in the view FS_GT_Tobak, and a notice for every active sales place
	 *         marked for tobacco that is not in the view
	 */
	public ReaderResult permits() {
		final var candidates = snapshot.permitView().keySet().stream()
			.map(fsId -> permit(fsId, placesById.get(fsId)))
			.toList();
		final var notices = placesById.values().stream()
			.filter(place -> !place.upphort() && place.tobak() && !snapshot.permitView().containsKey(place.fsId()))
			.map(place -> new Notice(new ItemKey(ALKOL, TILLSTAND, String.valueOf(place.fsId())), place.namn(), Outcome.MIGRERAS_INTE,
				NOTICE_TOBACCO_WITHOUT_PERMIT + " Aktivt försäljningsställe är markerat för tobak men har inget gällande tobakstillstånd"))
			.toList();
		return new ReaderResult(candidates, notices);
	}

	/**
	 * @return one notification per active sales place and notified product, and a notice for every active sales place
	 *         without any product and without a tobacco permit
	 */
	public ReaderResult notifications() {
		final var candidates = new ArrayList<Candidate>();
		final var notices = new ArrayList<Notice>();
		placesById.values().stream().filter(place -> !place.upphort()).forEach(place -> {
			Arrays.stream(Product.values())
				.filter(product -> product.isNotifiedOn(place))
				.forEach(product -> candidates.add(notification(place, product)));
			if (!hasNotification(place) && !place.tobak() && !snapshot.permitView().containsKey(place.fsId())) {
				notices.add(new Notice(new ItemKey(ALKOL, ANMALAN, notificationId(place, NO_PRODUCT)), place.namn(), Outcome.MIGRERAS_INTE,
					NOTICE_WITHOUT_PRODUCT + " Aktivt försäljningsställe utan produkttyp migreras inte"));
			}
		});
		return new ReaderResult(candidates, notices);
	}

	/**
	 * @return the inspection errands of the errand types in the settings, only of places with a current permit when the
	 *         settings say so, and never of a sales place that has ceased (section 7.7)
	 */
	public ReaderResult inspections() {
		final var inspections = new InspectionCandidates(ErrandType.TOBACCO_INSPECTION, properties, UNKNOWN_SALES_PLACE, "tillsynsärendet saknar försäljningsställe", today);
		return new ReaderResult(snapshot.errands().stream()
			.filter(errand -> !hasCeased(errand.fsId()))
			.filter(errand -> inspections.selects(errand.arendetyp(), hasPermit(errand.fsId())))
			.map(errand -> inspectionErrand(errand, inspections))
			.toList(), List.of());
	}

	/**
	 * @return the inspection visits, only of places with a current permit when the settings say so, and never of a sales
	 *         place that has ceased (section 7.7.2)
	 */
	public ReaderResult visits() {
		final var visits = new InspectionVisitCandidates(ErrandType.TOBACCO_INSPECTION_VISIT, properties, UNKNOWN_SALES_PLACE, "tillsynsbesöket saknar försäljningsställe", today);
		return new ReaderResult(snapshot.visits().visits().stream()
			.filter(visit -> !hasCeased(visit.objektId()))
			.filter(visit -> visits.selects(hasPermit(visit.objektId())))
			.map(visit -> visit(visit, visits))
			.toList(), List.of());
	}

	private Candidate permit(final Integer fsId, final SalesPlaceRow place) {
		final var hits = new ArrayList<RuleHit>();
		if (place == null) {
			hits.add(RuleHit.of(PLACE_MISSING, "försäljningsstället finns inte"));
		}
		final var owner = ownerOf(place);
		ownerHits(place, owner, hits);
		if (place != null && place.upphort()) {
			hits.add(RuleHit.of(SALES_PLACE_CEASED));
		}
		final var grant = latestGrant(fsId).orElse(null);
		final var grantErrand = grant == null ? null : errandsById.get(grant.arendeId());
		grantHits(fsId, grant, grantErrand, owner, hits);
		openErrandHits(grantErrand == null ? List.of() : List.of(grantErrand), hits);
		final var issued = grant == null ? null : Optional.ofNullable(grant.giltigtFrom()).orElse(grant.beslutsDatum());
		if (grant != null && issued == null) {
			hits.add(RuleHit.of(ISSUE_DATE_MISSING));
		}

		final var placeName = place == null ? null : place.namn();
		final var name = Optional.ofNullable(placeName).orElse(UNKNOWN_SALES_PLACE);
		final var diaryNumber = grantErrand == null ? null : grantErrand.diarieNr();
		final var products = place == null ? List.<String>of() : notifiedProducts(place);
		final var kindsText = Texts.join(", ", permitKinds(fsId, grant));
		final var holder = PartyRows.holder(owner, today);
		final var premises = place == null ? null : place(place);

		return Candidate.builder(new ItemKey(ALKOL, TILLSTAND, String.valueOf(fsId)), ErrandType.TOBACCO_PERMIT)
			.placeName(placeName)
			.ruleHits(hits)
			.holder(holder)
			.place(premises)
			.persons(owner == null ? List.of() : PartyRows.persons(snapshot.persons().getOrDefault(owner.id(), List.of()), code -> CodeTexts.textOf(snapshot.positions(), code), today))
			.title(Texts.title("Tobakstillstånd", name))
			.description(Texts.join("\n", Arrays.asList(
				"Tobakstillstånd för " + name + ", migrerat från " + ALKOL.getDisplayName() + ".",
				line("Tillståndstyp", kindsText),
				CandidateTexts.issuedLine(issued, diaryNumber),
				line("Anmälda produkter", Texts.join(", ", products)))))
			.labelPaths(properties.errandType(ErrandType.TOBACCO_PERMIT).labels())
			.externalTag(TAG_DIARY_NUMBER, diaryNumber)
			.parameters(new ParameterList()
				.add("alkolFsId", "Id i AlkOL", String.valueOf(fsId))
				.add("tillstandstyp", "Tillståndstyp", kindsText)
				.add("utfardat", "Utfärdat", SourceValues.format(issued))
				.add("diarienummer", "Diarienummer i AlkOL", diaryNumber)
				.add("tidigareBeviljanden", "Tidigare beviljanden", earlierGrantTexts(fsId, grant))
				.add("senasteBeslut", "Senaste beslut", latestDecisionText(fsId, grant))
				.add("anmaldaProdukter", "Anmälda produkter", products)
				.add("noteringar", "Noteringar", place == null ? null : place.notering())
				.add(CandidateTexts.migrated(today, ALKOL))
				.build())
			.asset(new AssetData(kindsText == null ? null : kindsText.toLowerCase(Locale.ROOT), "Tobakstillstånd för " + name, issued, null,
				permitAssetParameters(holder, premises, snapshot.permitView().get(fsId), diaryNumber)))
			.files(errandFiles(grantErrand == null ? List.of() : List.of(grantErrand.arendeId())))
			.build();
	}

	private Candidate notification(final SalesPlaceRow place, final Product product) {
		final var hits = new ArrayList<RuleHit>();
		final var owner = ownerOf(place);
		ownerHits(place, owner, hits);
		final var errands = errandsByPlace.getOrDefault(place.fsId(), List.of()).stream()
			.filter(errand -> product.isErrandType(errand.arendetyp()))
			.toList();
		final var events = eventsByPlace.getOrDefault(place.fsId(), List.of()).stream()
			.filter(event -> product.isEventType(event.handelseTyp()))
			.toList();
		final var notified = notifiedDate(place, product, errands, events, hits);
		openErrandHits(errands, hits);

		final var type = product.errandType(isServing(place, errands, events));
		final var diaryNumber = latestDiaryNumber(errands, events);
		final var name = Optional.ofNullable(place.namn()).orElse(UNKNOWN_SALES_PLACE);
		final var productText = productText(type);
		final var notifiedText = notified.date() == null ? null : SourceValues.format(notified.date()) + (notified.estimated() ? " (uppskattat)" : "");
		final var premises = place(place);
		final var assetParameters = new AssetParameters()
			.premises(premises, null)
			.text(AssetParameters.PRODUCT, product.parameterText())
			.text(AssetParameters.DIARY_NUMBER, diaryNumber)
			.text(AssetParameters.NOTIFIED_DATE_ESTIMATED, notified.estimated() ? Boolean.TRUE.toString() : null)
			.text(AssetParameters.MIGRATED_FROM, ALKOL.getDisplayName())
			.build();

		return Candidate.builder(new ItemKey(ALKOL, ANMALAN, notificationId(place, product.code())), type)
			.placeName(place.namn())
			.ruleHits(hits)
			.holder(PartyRows.holder(owner, today))
			.place(premises)
			.title(Texts.title("Anmälan om " + productText, name))
			.description(Texts.join("\n", Arrays.asList(
				"Anmälan om " + productText + " för " + name + ", migrerad från " + ALKOL.getDisplayName() + ".",
				line("Anmäld", notifiedText),
				line("Diarienummer", diaryNumber))))
			.labelPaths(properties.errandType(type).labels())
			.externalTag(TAG_NOTIFICATION, notificationId(place, product.code()))
			.externalTag(TAG_DIARY_NUMBER, diaryNumber)
			.parameters(new ParameterList()
				.add("alkolFsId", "Id i AlkOL", String.valueOf(place.fsId()))
				.add("produkt", "Produkt", product.parameterText())
				.add("anmald", "Anmäld", SourceValues.format(notified.date()))
				.add("anmaldUppskattat", "Datumet är uppskattat", notified.estimated() ? "Ja" : null)
				.add("diarienummer", "Diarienummer i AlkOL", diaryNumber)
				.add(CandidateTexts.migrated(today, ALKOL))
				.build())
			.asset(new AssetData(null, "Anmälan om " + productText + " på " + name, notified.date(), null, assetParameters))
			.files(errandFiles(errands.stream().map(ErrandRow::arendeId).toList()))
			.build();
	}

	private Candidate inspectionErrand(final ErrandRow errand, final InspectionCandidates inspections) {
		final var place = errand.fsId() == null ? null : placesById.get(errand.fsId());
		final var source = new InspectionErrand(ALKOL, errand.arendeId(), errand.arendetyp(), CodeTexts.textOf(snapshot.errandTypes(), errand.arendetyp()), errand.diarieNr(), errand.oppnad(),
			errand.avslutad());
		final var applicant = errand.sokandeId() == null ? null : snapshot.applicants().get(errand.sokandeId());
		final var permitKey = hasPermit(errand.fsId()) ? new ItemKey(ALKOL, TILLSTAND, String.valueOf(errand.fsId())) : null;
		final var decisions = decisionsByErrand.getOrDefault(errand.arendeId(), List.of()).stream()
			.sorted(DECISION_ORDER)
			.map(this::decisionText)
			.filter(Objects::nonNull)
			.toList();

		return inspections.builder(source, place == null ? null : place(place), applicant, ownerOf(place), permitKey, decisions)
			.files(errandFiles(List.of(errand.arendeId())))
			.build();
	}

	private Candidate visit(final VisitRow visit, final InspectionVisitCandidates visits) {
		final var place = visit.objektId() == null ? null : placesById.get(visit.objektId());
		final var files = new FileList(sharedFileNames);
		snapshot.visits().forEachFile(visit, files::add);
		return visits.builder(visit, snapshot.visits(), place == null ? null : place(place))
			.files(files.build())
			.build();
	}

	/**
	 * The rules about the owner of the sales place: G21, G8, G20 and G7.
	 */
	private void ownerHits(final SalesPlaceRow place, final ContactRow owner, final List<RuleHit> hits) {
		if (place == null) {
			return;
		}
		if (owner == null) {
			hits.add(RuleHit.of(OWNER_MISSING, place.agarId() == null ? "ingen ägare kopplad" : "AgarID %d finns inte".formatted(place.agarId())));
			return;
		}
		final var holder = PartyRows.holder(owner, today);
		if (holder.legalId() == null) {
			hits.add(RuleHit.of(LEGAL_ID_INVALID, "AgarID " + owner.id()));
		}
		final var ownerNumber = PartyRows.numberKey(owner.organisationsNr(), today);
		final var copyNumber = PartyRows.numberKey(place.agareOrgNr(), today);
		if (copyNumber.isPresent() && !copyNumber.equals(ownerNumber)) {
			hits.add(RuleHit.of(OWNER_COPY_DIFFERS));
		}
		ownerNumber.filter(number -> ownersByNumber.getOrDefault(number, Set.of()).size() > 1)
			.ifPresent(_ -> hits.add(RuleHit.of(LEGAL_ID_ON_SEVERAL_OWNERS, "AgarID " + owner.id())));
	}

	/**
	 * The rules about the granting decision: G15, G18, G16 and G19.
	 */
	private void grantHits(final Integer fsId, final DecisionRow grant, final ErrandRow grantErrand, final ContactRow owner, final List<RuleHit> hits) {
		if (grant == null) {
			hits.add(RuleHit.of(DIARY_NUMBER_MISSING, "inget beviljande beslut hittades"));
			return;
		}
		decisionsOf(fsId).stream()
			.filter(decision -> isOneOf(decision.beslutsTyp(), ENDING_DECISIONS))
			.filter(decision -> DECISION_ORDER.compare(decision, grant) > 0)
			.max(DECISION_ORDER)
			.ifPresent(ending -> hits.add(RuleHit.of(PERMIT_ENDED, Texts.joinWords(ending.beslutsTyp(), SourceValues.format(ending.beslutsDatum())))));
		final var diaryNumber = grantErrand == null ? null : DiaryNumbers.normalize(grantErrand.diarieNr());
		if (diaryNumber == null) {
			hits.add(RuleHit.of(DIARY_NUMBER_MISSING, "det beviljande ärendet saknar diarienummer"));
		} else if (permitsByDiaryNumber.getOrDefault(diaryNumber, 0) > 1) {
			hits.add(RuleHit.of(DIARY_NUMBER_ON_SEVERAL, grantErrand.diarieNr()));
		}
		final var applicantNumber = Optional.ofNullable(grantErrand).map(ErrandRow::sokandeId).map(snapshot.applicants()::get)
			.flatMap(applicant -> PartyRows.numberKey(applicant.organisationsNr(), today));
		final var ownerNumber = Optional.ofNullable(owner).flatMap(found -> PartyRows.numberKey(found.organisationsNr(), today));
		if (applicantNumber.isPresent() && ownerNumber.isPresent() && !applicantNumber.equals(ownerNumber)) {
			hits.add(RuleHit.of(APPLICANT_NOT_OWNER));
		}
	}

	/**
	 * Ä1 for every errand the permit or notification builds on that is not closed in AlkOL (decision 23).
	 */
	private static void openErrandHits(final List<ErrandRow> errands, final List<RuleHit> hits) {
		errands.stream()
			.filter(errand -> errand.avslutad() == null)
			.forEach(errand -> hits.add(RuleHit.of(UNDERLYING_ERRAND_OPEN, CandidateTexts.errand(errand.arendeId(), errand.diarieNr()))));
	}

	/**
	 * @return the date of the notification: the first registration of the product, or, when the product is only a mark on
	 *         the sales place, the earliest date of the place (G22); no date at all hits G23
	 */
	private NotifiedDate notifiedDate(final SalesPlaceRow place, final Product product, final List<ErrandRow> errands, final List<EventRow> events, final List<RuleHit> hits) {
		if (errands.isEmpty() && events.isEmpty()) {
			hits.add(RuleHit.of(NOTIFICATION_ONLY_FLAG, product.parameterText()));
			final var earliest = earliestDate(place.fsId()).orElse(null);
			if (earliest == null) {
				hits.add(RuleHit.of(NOTIFICATION_DATE_MISSING, product.parameterText()));
			}
			return new NotifiedDate(earliest, earliest != null);
		}
		final var first = Stream.concat(errands.stream().map(ErrandRow::oppnad), events.stream().map(EventRow::tidpunkt).filter(Objects::nonNull).map(LocalDateTime::toLocalDate))
			.filter(Objects::nonNull)
			.min(Comparator.naturalOrder())
			.orElse(null);
		if (first == null) {
			hits.add(RuleHit.of(NOTIFICATION_DATE_MISSING, product.parameterText() + ", anmälan saknar datum"));
		}
		return new NotifiedDate(first, false);
	}

	/**
	 * @return the diary number of the latest errand of the notification, else of its latest event
	 */
	private static String latestDiaryNumber(final List<ErrandRow> errands, final List<EventRow> events) {
		return errands.stream()
			.filter(errand -> errand.diarieNr() != null)
			.max(Comparator.comparing(ErrandRow::oppnad, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(ErrandRow::arendeId))
			.map(ErrandRow::diarieNr)
			.or(() -> events.stream()
				.filter(event -> event.diarieNr() != null)
				.max(Comparator.comparing(EventRow::tidpunkt, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(EventRow::handelseId, Comparator.nullsFirst(Comparator.naturalOrder())))
				.map(EventRow::diarieNr))
			.orElse(null);
	}

	/**
	 * @return the granting decisions before the latest one, as "date, diary number"
	 */
	private List<String> earlierGrantTexts(final Integer fsId, final DecisionRow grant) {
		if (grant == null) {
			return List.of();
		}
		return decisionsOf(fsId).stream()
			.filter(decision -> isOneOf(decision.beslutsTyp(), GRANTING_DECISIONS))
			.filter(decision -> DECISION_ORDER.compare(decision, grant) < 0)
			.sorted(DECISION_ORDER)
			.map(decision -> Texts.join(", ", Arrays.asList(SourceValues.format(decision.beslutsDatum()), Optional.ofNullable(errandsById.get(decision.arendeId())).map(ErrandRow::diarieNr).orElse(null))))
			.filter(Objects::nonNull)
			.toList();
	}

	/**
	 * @return the latest decision after the granting one, as "type, date"; null when there is none
	 */
	private String latestDecisionText(final Integer fsId, final DecisionRow grant) {
		if (grant == null) {
			return null;
		}
		return decisionsOf(fsId).stream()
			.filter(decision -> DECISION_ORDER.compare(decision, grant) > 0)
			.max(DECISION_ORDER)
			.map(this::decisionText)
			.orElse(null);
	}

	private String decisionText(final DecisionRow decision) {
		return Texts.join(", ", Arrays.asList(CodeTexts.textOf(snapshot.decisionTypes(), decision.beslutsTyp()), SourceValues.format(decision.beslutsDatum())));
	}

	private boolean hasPermit(final Integer fsId) {
		return fsId != null && snapshot.permitView().containsKey(fsId);
	}

	private boolean hasCeased(final Integer fsId) {
		return fsId != null && Optional.ofNullable(placesById.get(fsId)).map(SalesPlaceRow::upphort).orElse(false);
	}

	private ContactRow ownerOf(final SalesPlaceRow place) {
		return place == null || place.agarId() == null ? null : snapshot.owners().get(place.agarId());
	}

	private Place place(final SalesPlaceRow place) {
		final var area = CodeTexts.textOf(snapshot.areas(), place.omrade());
		return new Place(place.namn(), place.gatuadress(), place.postnummer(), place.postort(), place.telefon(), place.email(), area, new ParameterList()
			.add("kategori", "Kategori", CodeTexts.textOf(snapshot.categories(), place.kategori()))
			.add("omrade", "Område", area)
			.build());
	}

	private List<DecisionRow> decisionsOf(final Integer fsId) {
		return decisionsByPlace.getOrDefault(fsId, List.of());
	}

	private Optional<DecisionRow> latestGrant(final Integer fsId) {
		return decisionsOf(fsId).stream()
			.filter(decision -> isOneOf(decision.beslutsTyp(), GRANTING_DECISIONS))
			.max(DECISION_ORDER);
	}

	/**
	 * The kinds of permit: the columns of the view, or the type of the latest granting decision when the view shows none.
	 */
	private List<String> permitKinds(final Integer fsId, final DecisionRow grant) {
		final var view = snapshot.permitView().get(fsId);
		final var kinds = new ArrayList<String>();
		if (view != null) {
			if (view.retail()) {
				kinds.add("Detaljhandel");
			}
			if (view.wholesale()) {
				kinds.add("Partihandel");
			}
			if (view.distance()) {
				kinds.add("Distanshandel");
			}
		}
		if (kinds.isEmpty() && grant != null && grant.beslutsTyp() != null) {
			switch (grant.beslutsTyp().toUpperCase(Locale.ROOT)) {
				case "GTDE" -> kinds.add("Detaljhandel");
				case "GTPH" -> kinds.add("Partihandel");
				case "GTDI" -> kinds.add("Distanshandel");
				default -> {
					// Not a granting decision, no kind to add
				}
			}
		}
		return kinds;
	}

	private static List<String> notifiedProducts(final SalesPlaceRow place) {
		return Arrays.stream(Product.values()).filter(product -> product.isNotifiedOn(place)).map(Product::parameterText).toList();
	}

	/**
	 * @return the id of the notification of a product on a sales place, e.g. 12-FOLKOL
	 */
	private static String notificationId(final SalesPlaceRow place, final String productCode) {
		return place.fsId() + "-" + productCode;
	}

	private static boolean hasNotification(final SalesPlaceRow place) {
		return Arrays.stream(Product.values()).anyMatch(product -> product.isNotifiedOn(place));
	}

	/**
	 * Whether a notification of folköl is about serving: the latest notification of folköl decides, otherwise the category
	 * of the sales place (section 7.8.5).
	 */
	private boolean isServing(final SalesPlaceRow place, final List<ErrandRow> errands, final List<EventRow> events) {
		record Registration(LocalDateTime when, boolean serving) {
		}
		final var latest = Stream.concat(
			errands.stream().map(errand -> new Registration(Optional.ofNullable(errand.oppnad()).map(LocalDate::atStartOfDay).orElse(null), Product.FOLKOL.isServingCode(errand.arendetyp()))),
			events.stream().map(event -> new Registration(event.tidpunkt(), Product.FOLKOL.isServingCode(event.handelseTyp()))))
			.max(Comparator.comparing(Registration::when, Comparator.nullsFirst(Comparator.naturalOrder())));
		if (latest.isPresent()) {
			return latest.get().serving();
		}
		return Optional.ofNullable(CodeTexts.textOf(snapshot.categories(), place.kategori()))
			.map(text -> text.toLowerCase(Locale.ROOT).startsWith(SERVING_PLACE_CATEGORY_PREFIX))
			.orElse(false);
	}

	/**
	 * @return the earliest date of the sales place's errands and events
	 */
	private Optional<LocalDate> earliestDate(final Integer fsId) {
		return Stream.concat(
			errandsByPlace.getOrDefault(fsId, List.of()).stream().map(ErrandRow::oppnad),
			eventsByPlace.getOrDefault(fsId, List.of()).stream().map(EventRow::tidpunkt).filter(Objects::nonNull).map(LocalDateTime::toLocalDate))
			.filter(Objects::nonNull)
			.min(Comparator.naturalOrder());
	}

	private List<FileReference> errandFiles(final Collection<Integer> arendeIds) {
		final var files = new FileList(sharedFileNames);
		for (final var arendeId : arendeIds) {
			eventsByErrand.getOrDefault(arendeId, List.of()).forEach(event -> files.add(event.worddokument(), DOCUMENTS).add(event.bifogadfil(), ATTACHMENTS));
			decisionsByErrand.getOrDefault(arendeId, List.of()).forEach(decision -> files.add(decision.worddokument(), DOCUMENTS).add(decision.bifogadfil(), ATTACHMENTS));
		}
		return files.build();
	}

	/**
	 * Every file reference of AlkOL with the sales place it belongs to: the place of the errand it goes to, or the errand
	 * itself when the errand has no place. An event without an errand belongs to its own place, or is a place of its own.
	 */
	private SharedFileNames sharedFileNames() {
		final var names = SharedFileNames.builder(properties.fileArea(ALKOL));
		snapshot.events().forEach(event -> {
			final var withoutErrand = event.fsId() != null ? "fs:" + event.fsId() : "handelse:" + event.handelseId();
			final var place = placeKeyOfErrand(event.arendeId(), withoutErrand);
			names.add(place, event.worddokument(), DOCUMENTS).add(place, event.bifogadfil(), ATTACHMENTS);
		});
		snapshot.decisions().forEach(decision -> {
			final var place = placeKeyOfErrand(decision.arendeId(), "beslut:" + decision.beslutsId());
			names.add(place, decision.worddokument(), DOCUMENTS).add(place, decision.bifogadfil(), ATTACHMENTS);
		});
		snapshot.visits().visits().forEach(visit -> {
			final var place = visit.objektId() == null ? "besok:" + visit.tillfalleNr() : "fs:" + visit.objektId();
			snapshot.visits().forEachFile(visit, (name, folder) -> names.add(place, name, folder));
		});
		return names.build();
	}

	private String placeKeyOfErrand(final Integer arendeId, final String withoutErrand) {
		if (arendeId == null) {
			return withoutErrand;
		}
		return Optional.ofNullable(errandsById.get(arendeId))
			.map(ErrandRow::fsId)
			.map(fsId -> "fs:" + fsId)
			.orElse("arende:" + arendeId);
	}

	/**
	 * The parameters of the tobacco permit, under the keys of the AoT solution (section 7.4). The kinds of sale come from
	 * the view FS_GT_Tobak.
	 */
	private static Map<String, String> permitAssetParameters(final Holder holder, final Place premises, final PermitViewRow view, final String diaryNumber) {
		final var parameters = new AssetParameters()
			.holder(holder)
			.premises(premises, null);
		Optional.ofNullable(view).ifPresent(kinds -> parameters
			.flag(AssetParameters.SALES_RETAIL, kinds.retail())
			.flag(AssetParameters.SALES_WHOLESALE, kinds.wholesale())
			.flag(AssetParameters.SALES_ONLINE, kinds.distance()));
		return parameters
			.text(AssetParameters.CASE_NUMBER, diaryNumber)
			.text(AssetParameters.DIARY_NUMBER, diaryNumber)
			.text(AssetParameters.MIGRATED_FROM, ALKOL.getDisplayName())
			.build();
	}

	private static String productText(final ErrandType type) {
		return switch (type) {
			case FOLKOL_SALES_NOTIFICATION -> "försäljning av folköl";
			case FOLKOL_SERVING_NOTIFICATION -> "servering av folköl";
			case ECIGARETTE_NOTIFICATION -> "försäljning av elektroniska cigaretter och påfyllningsbehållare";
			case TOBACCO_FREE_NICOTINE_NOTIFICATION -> "försäljning av tobaksfria nikotinprodukter";
			default -> throw new IllegalArgumentException("Inte en anmälan: " + type);
		};
	}

	private static boolean isOneOf(final String code, final Set<String> codes) {
		return code != null && codes.stream().anyMatch(code::equalsIgnoreCase);
	}

	private void indexErrands() {
		snapshot.errands().forEach(errand -> {
			errandsById.put(errand.arendeId(), errand);
			Optional.ofNullable(errand.fsId()).ifPresent(fsId -> errandsByPlace.computeIfAbsent(fsId, _ -> new ArrayList<>()).add(errand));
		});
	}

	private void indexDecisions() {
		snapshot.decisions().stream().filter(decision -> decision.arendeId() != null).forEach(decision -> {
			decisionsByErrand.computeIfAbsent(decision.arendeId(), _ -> new ArrayList<>()).add(decision);
			Optional.ofNullable(errandsById.get(decision.arendeId())).map(ErrandRow::fsId)
				.ifPresent(fsId -> decisionsByPlace.computeIfAbsent(fsId, _ -> new ArrayList<>()).add(decision));
		});
	}

	private void indexEvents() {
		snapshot.events().forEach(event -> {
			Optional.ofNullable(event.fsId()).ifPresent(fsId -> eventsByPlace.computeIfAbsent(fsId, _ -> new ArrayList<>()).add(event));
			Optional.ofNullable(event.arendeId()).ifPresent(arendeId -> eventsByErrand.computeIfAbsent(arendeId, _ -> new ArrayList<>()).add(event));
		});
	}

	/**
	 * Counts the owners per number, for G7. Only the owners of the places the migration selects count, as in the review
	 * query.
	 */
	private void indexOwnerNumbers() {
		placesById.values().stream()
			.filter(place -> snapshot.permitView().containsKey(place.fsId()) || (!place.upphort() && hasNotification(place)))
			.map(this::ownerOf)
			.filter(Objects::nonNull)
			.forEach(owner -> PartyRows.numberKey(owner.organisationsNr(), today)
				.ifPresent(number -> ownersByNumber.computeIfAbsent(number, _ -> new HashSet<>()).add(owner.id())));
	}

	private void indexDiaryNumbers() {
		snapshot.permitView().keySet().forEach(fsId -> latestGrant(fsId)
			.map(grant -> errandsById.get(grant.arendeId()))
			.map(errand -> DiaryNumbers.normalize(errand.diarieNr()))
			.ifPresent(number -> permitsByDiaryNumber.merge(number, 1, Integer::sum)));
	}

	/**
	 * The date of a notification.
	 *
	 * @param date      the date, null when unknown
	 * @param estimated true when the date is the earliest date of the sales place rather than of the notification
	 */
	private record NotifiedDate(LocalDate date, boolean estimated) {
	}

	/**
	 * The products that are notified, with the errand types and event types that register a notification (section
	 * 7.8.5).
	 */
	enum Product {

		FOLKOL("FOLKOL", "Folköl", Set.of("Fföl", "SÖL"), Set.of("014", "015", "BeaFol"), Set.of("SÖL", "015")),
		ECIGARETTER("ECIGARETTER", "E-cigaretter och påfyllningsbehållare", Set.of("ec"), Set.of("105", "BekAnm"), Set.of()),
		TOBAKSFRI_NIKOTIN("TOBAKSFRI_NIKOTIN", "Tobaksfria nikotinprodukter", Set.of("tn"), Set.of("047"), Set.of());

		private final String code;
		private final String parameterText;
		private final Set<String> errandTypes;
		private final Set<String> eventTypes;
		private final Set<String> servingCodes;

		Product(final String code, final String parameterText, final Set<String> errandTypes, final Set<String> eventTypes, final Set<String> servingCodes) {
			this.code = code;
			this.parameterText = parameterText;
			this.errandTypes = errandTypes;
			this.eventTypes = eventTypes;
			this.servingCodes = servingCodes;
		}

		/**
		 * @return the code of the product in the id of the notification, e.g. 1234-FOLKOL; it must never change once a
		 *         notification has been migrated
		 */
		String code() {
			return code;
		}

		String parameterText() {
			return parameterText;
		}

		boolean isNotifiedOn(final SalesPlaceRow place) {
			return switch (this) {
				case FOLKOL -> place.folkol();
				case ECIGARETTER -> place.ecigaretter();
				case TOBAKSFRI_NIKOTIN -> place.tobaksfriNikotin();
			};
		}

		boolean isErrandType(final String code) {
			return isOneOf(code, errandTypes);
		}

		boolean isEventType(final String code) {
			return isOneOf(code, eventTypes);
		}

		/**
		 * @return true for an errand type or event type that notifies serving rather than sales of the product
		 */
		boolean isServingCode(final String code) {
			return isOneOf(code, servingCodes);
		}

		ErrandType errandType(final boolean serving) {
			return switch (this) {
				case FOLKOL -> serving ? ErrandType.FOLKOL_SERVING_NOTIFICATION : ErrandType.FOLKOL_SALES_NOTIFICATION;
				case ECIGARETTER -> ErrandType.ECIGARETTE_NOTIFICATION;
				case TOBAKSFRI_NIKOTIN -> ErrandType.TOBACCO_FREE_NICOTINE_NOTIFICATION;
			};
		}
	}
}
