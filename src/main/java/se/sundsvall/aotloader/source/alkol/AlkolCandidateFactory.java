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
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.FileReference.FileFolder;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Person;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.PlacePermit;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReaderResult.Notice;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.AssetParameters;
import se.sundsvall.aotloader.source.CandidateTexts;
import se.sundsvall.aotloader.source.CodeTexts;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.ErrandParameters;
import se.sundsvall.aotloader.source.FileList;
import se.sundsvall.aotloader.source.FilePurposes;
import se.sundsvall.aotloader.source.InspectionCandidates;
import se.sundsvall.aotloader.source.InspectionCandidates.InspectionErrand;
import se.sundsvall.aotloader.source.InspectionVisitCandidates;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.source.Journal;
import se.sundsvall.aotloader.source.ParameterList;
import se.sundsvall.aotloader.source.PartyRows;
import se.sundsvall.aotloader.source.PermitFiles;
import se.sundsvall.aotloader.source.PermitInvestigations;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.EventRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.PermitViewRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.SalesPlaceRow;
import se.sundsvall.aotloader.source.files.SharedFileNames;
import se.sundsvall.aotloader.support.DiaryNumbers;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static java.util.stream.Collectors.toSet;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_CERTIFICATE;
import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_DRAWING;
import static se.sundsvall.aotloader.Constants.TAG_DIARY_NUMBER;
import static se.sundsvall.aotloader.Constants.TAG_NOTIFICATION;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.ReviewRule.APPLICANT_NOT_OWNER;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_OLDER;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_ON_SEVERAL;
import static se.sundsvall.aotloader.service.model.ReviewRule.ISSUE_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_INVALID;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_ON_SEVERAL_OWNERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_OF_FORMER_HOLDER;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_ONLY_FLAG;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_COPY_DIFFERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERMIT_ENDED;
import static se.sundsvall.aotloader.service.model.ReviewRule.PLACE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.SALES_PLACE_CEASED;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNDERLYING_ERRAND_OPEN;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNPARSEABLE;
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

	private static final Set<String> GRANTING_DECISIONS = Arrays.stream(PermitKind.values()).map(PermitKind::decision).collect(toUnmodifiableSet());
	/**
	 * The templates of the tobacco permit certificate, of Sundsvall and of Timrå, made at the granting decision (decision
	 * 34).
	 */
	private static final Set<String> CERTIFICATE_TEMPLATES = Set.of("TILLSTT", "TILLTT");
	private static final Set<String> ENDING_DECISIONS = Set.of("GTDEU", "GTDEÅ");
	private static final String UNDATED = "utan datum";
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
	 * @return the codes of the products in the ids of the notifications, e.g. FOLKOL in 1234-FOLKOL
	 */
	public static Set<String> productCodes() {
		return Arrays.stream(Product.values()).map(Product::code).collect(toUnmodifiableSet());
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
			.map(place -> new Notice(new ItemKey(ALKOL, TILLSTAND, String.valueOf(place.fsId())), place.namn(), Outcome.MIGRERAS_INTE, NOTICE_TOBACCO_WITHOUT_PERMIT,
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
				notices.add(new Notice(new ItemKey(ALKOL, ANMALAN, notificationId(place, NO_PRODUCT)), place.namn(), Outcome.MIGRERAS_INTE, NOTICE_WITHOUT_PRODUCT,
					NOTICE_WITHOUT_PRODUCT + " Aktivt försäljningsställe utan produkttyp migreras inte"));
			}
		});
		return new ReaderResult(candidates, notices);
	}

	/**
	 * @return the inspection errands of the errand types in the settings, only of places with a current permit when the
	 *         settings say so, and never of a sales place that has ceased (section 7.7). A permit is current when it is in
	 *         the view FS_GT_Tobak and no decision ended it after its latest grant (G18, decision 29).
	 */
	public ReaderResult inspections() {
		final var inspections = new InspectionCandidates(ErrandType.TOBACCO_INSPECTION, properties, UNKNOWN_SALES_PLACE, "tillsynsärendet saknar försäljningsställe", today);
		return new ReaderResult(snapshot.errands().stream()
			.filter(errand -> !hasCeased(errand.fsId()))
			.filter(errand -> inspections.selects(errand.arendetyp(), hasCurrentPermit(errand.fsId())))
			.map(errand -> inspectionErrand(errand, inspections))
			.toList(), List.of());
	}

	/**
	 * @return the inspection visits, only of places with a current permit, as for the inspection errands, when the settings
	 *         say so, and never of a sales place that has ceased (section 7.7.2)
	 */
	public ReaderResult visits() {
		final var visits = new InspectionVisitCandidates(ErrandType.TOBACCO_INSPECTION_VISIT, properties, UNKNOWN_SALES_PLACE, "tillsynsbesöket saknar försäljningsställe", today);
		return new ReaderResult(snapshot.visits().visits().stream()
			.filter(visit -> !hasCeased(visit.objektId()))
			.filter(visit -> visits.selects(hasCurrentPermit(visit.objektId())))
			.map(visit -> visit(visit, visits))
			.toList(), List.of());
	}

	private Candidate permit(final Integer fsId, final SalesPlaceRow place) {
		final var owner = ownerOf(place);
		final var grant = latestGrant(fsId).orElse(null);
		final var grantErrand = grant == null ? null : errandsById.get(grant.arendeId());
		final var issued = issued(grant);
		final var view = snapshot.permitView().get(fsId);
		final var hits = permitHits(fsId, place, owner, grant, grantErrand, issued);
		if (view != null && view.unreadable()) {
			hits.add(RuleHit.of(UNPARSEABLE, "tillståndstypen i FS_GT_Tobak går inte att tolka"));
		}

		final var placeName = place == null ? null : place.namn();
		final var name = Optional.ofNullable(placeName).orElse(UNKNOWN_SALES_PLACE);
		final var notes = place == null ? null : place.notering();
		final var diaryNumber = grantErrand == null ? null : grantErrand.diarieNr();
		final var products = place == null ? List.<String>of() : notifiedProducts(place);
		final var kinds = permitKinds(view, grant);
		final var kindsText = Texts.join(", ", kinds.stream().map(PermitKind::text).toList());
		final var holder = PartyRows.holder(owner, today);
		final var premises = place == null ? null : place(place);
		final var files = permitFiles(place, fsId, grant, grantErrand, hits);
		final var title = Texts.title("Tobakstillstånd", name);
		final var log = eventsWithoutErrand(fsId)
			.filter(event -> event.text() != null && !registersNotification(place, event))
			.map(event -> journalEntry(event, null))
			.toList();

		return Candidate.builder(new ItemKey(ALKOL, TILLSTAND, String.valueOf(fsId)), ErrandType.TOBACCO_PERMIT)
			.placeName(placeName)
			.ruleHits(hits)
			.holder(holder)
			.place(premises)
			.persons(persons(owner))
			.title(title)
			.investigation(permitInvestigation(title, grant, grantErrand, notes))
			.description(Texts.join("\n", Arrays.asList(
				"Tobakstillstånd för " + name + ", migrerat från " + ALKOL.getDisplayName() + ".",
				line("Tillståndstyp", kindsText),
				CandidateTexts.issuedLine(issued, diaryNumber),
				line("Anmälda produkter", Texts.join(", ", products)))))
			.labelPaths(properties.errandType(ErrandType.TOBACCO_PERMIT).labels())
			.externalTag(TAG_DIARY_NUMBER, diaryNumber)
			.parameters(new ParameterList()
				.add(ErrandParameters.ALKOL_FS_ID, "Id i AlkOL", String.valueOf(fsId))
				.add("tillstandstyp", "Tillståndstyp", kindsText)
				.add(ErrandParameters.ISSUED, "Utfärdat", SourceValues.format(issued))
				.add(ErrandParameters.DIARY_NUMBER, "Diarienummer i AlkOL", diaryNumber)
				.add(ErrandParameters.HANDLERS, "Handläggare i AlkOL", grantErrand == null ? List.of() : snapshot.visits().handlerNames(grantErrand.handlaggare()))
				.add("tidigareBeviljanden", "Tidigare beviljanden", earlierGrantTexts(fsId, grant))
				.add(ErrandParameters.LATEST_DECISION, "Senaste beslut i AlkOL", latestDecisionText(fsId, grant))
				.add("anmaldaProdukter", "Anmälda produkter", products)
				.add(ErrandParameters.NOTES, "Noteringar i AlkOL", notes)
				.add(ErrandParameters.EVENT_LOG, "Händelselogg i AlkOL", Journal.lines(log))
				.add(CandidateTexts.migrated(today, ALKOL))
				.build())
			.asset(new AssetData(kindsText == null ? null : kindsText.toLowerCase(Locale.ROOT), "Tobakstillstånd för " + name, issued, null,
				permitAssetParameters(holder, premises, kinds, diaryNumber), legalBasisDocuments(fsId, grant, files)))
			.files(files.build(), files.assetFiles(), files.details())
			.build();
	}

	/**
	 * The rules about the tobacco permit that its place, its owner and its latest granting decision decide: G1, those of
	 * the owner, G25, those of the grant, Ä1 and G9.
	 *
	 * @return the hits, to which the files of the permit add theirs
	 */
	private List<RuleHit> permitHits(final Integer fsId, final SalesPlaceRow place, final ContactRow owner, final DecisionRow grant, final ErrandRow grantErrand,
		final LocalDate issued) {
		final var hits = new ArrayList<RuleHit>();
		if (place == null) {
			hits.add(RuleHit.of(PLACE_MISSING, "försäljningsstället finns inte"));
		}
		ownerHits(place, owner, hits);
		if (place != null && place.upphort()) {
			hits.add(RuleHit.of(SALES_PLACE_CEASED));
		}
		grantHits(fsId, grant, grantErrand, owner, hits);
		openErrandHits(grantErrand == null ? List.of() : List.of(grantErrand), hits);
		if (grant != null && issued == null) {
			hits.add(RuleHit.of(ISSUE_DATE_MISSING));
		}
		return hits;
	}

	/**
	 * @return the day the tobacco permit was issued: the first day of the latest grant, else the day it was decided; null
	 *         without a grant
	 */
	private static LocalDate issued(final DecisionRow grant) {
		return grant == null ? null : Optional.ofNullable(grant.giltigtFrom()).orElse(grant.beslutsDatum());
	}

	private List<Person> persons(final ContactRow owner) {
		return owner == null ? List.of() : PartyRows.persons(snapshot.persons().getOrDefault(owner.id(), List.of()), code -> CodeTexts.textOf(snapshot.positions(), code), today);
	}

	/**
	 * @param  shownNote the note of the place, which the permit already shows as a parameter; null when none
	 * @return           the investigation of the tobacco permit from the errand of its latest grant (decision 46), without
	 *                   the note already shown
	 */
	private InvestigationData permitInvestigation(final String title, final DecisionRow grant, final ErrandRow grantErrand, final String shownNote) {
		return PermitInvestigations.of(title, Stream.ofNullable(grantErrand).map(ErrandRow::oppnad).toList(),
			Stream.ofNullable(grantErrand).map(ErrandRow::avslutad).toList(), grant == null ? null : grant.beslutsDatum(),
			grantErrand == null ? InvestigationTexts.NONE : investigationTexts(grantErrand, shownNote));
	}

	/**
	 * The files of the tobacco permit: those of the errand of the latest granting decision (decision 7), and those of the
	 * place's events without an errand that register no notification (decision 37). The certificate, and the drawings and
	 * the permit decisions of that errand, also go on the post in PartyAssets, in that order of precedence (decisions 34,
	 * 35 and 39). A certificate of an earlier grant than the latest may be a former holder's: it goes on the errand only
	 * and hits G29, as does a permit without a certificate.
	 */
	private FileList permitFiles(final SalesPlaceRow place, final Integer fsId, final DecisionRow grant, final ErrandRow grantErrand, final List<RuleHit> hits) {
		final var arendeIds = grantErrand == null ? List.<Integer>of() : List.of(grantErrand.arendeId());
		final var files = addErrandFiles(arendeIds, new FileList(sharedFileNames));
		eventsWithoutErrand(fsId)
			.filter(event -> !registersNotification(place, event))
			.forEach(event -> addEventFiles(files, event));

		certificate(fsId).ifPresentOrElse(certificate -> {
			files.add(certificate.name(), certificate.folder(), certificate.details());
			if (grant != null && isAfter(grant, certificate.decision())) {
				files.addToErrandOnly(certificate.name(), certificate.folder());
				hits.add(RuleHit.of(CERTIFICATE_OLDER, "beviset hör till beslutet %s, det senaste beviljandet är %s"
					.formatted(dateText(certificate.decision()), dateText(grant))));
			} else {
				files.add(certificate.name(), certificate.folder(), ASSET_CATEGORY_CERTIFICATE);
			}
		}, () -> hits.add(RuleHit.of(CERTIFICATE_MISSING)));
		arendeIds.forEach(arendeId -> {
			eventsByErrand.getOrDefault(arendeId, List.of()).stream()
				.filter(event -> FilePurposes.isDrawing(attachmentPurpose(event)))
				.forEach(event -> files.add(event.bifogadfil(), ATTACHMENTS, ASSET_CATEGORY_DRAWING));
			decisionsByErrand.getOrDefault(arendeId, List.of()).stream()
				.filter(decision -> PermitFiles.isDrawing(decision.bifogadfil()))
				.forEach(decision -> files.add(decision.bifogadfil(), ATTACHMENTS, ASSET_CATEGORY_DRAWING));
			decisionsByErrand.getOrDefault(arendeId, List.of()).forEach(decision -> PermitFiles.markPermitDecision(files, decision.mall(), decision.worddokument(), decision.bifogadfil(), CERTIFICATE_TEMPLATES));
		});
		return files;
	}

	/**
	 * @return the documents that state the legal basis of the permit, which AlkOL keeps nowhere else (decision 58): the
	 *         certificate, and then the document of the latest grant; none that the permit keeps on its errand only, such
	 *         as the certificate of an earlier grant
	 */
	private List<FileReference> legalBasisDocuments(final Integer fsId, final DecisionRow grant, final FileList files) {
		final var certificate = certificate(fsId).flatMap(found -> files.findOfHolder(found.name(), found.folder()));
		final var grantDocument = Optional.ofNullable(grant).flatMap(row -> files.findOfHolder(row.worddokument(), DOCUMENTS));
		return Stream.of(certificate, grantDocument).flatMap(Optional::stream).toList();
	}

	/**
	 * @return the latest certificate of the place: the document of a granting decision made from the template of the
	 *         certificate, or else an attachment of the decision whose name tells it is a certificate
	 */
	private Optional<Certificate> certificate(final Integer fsId) {
		return decisionsOf(fsId).stream()
			.filter(decision -> CodeTexts.isOneOf(decision.beslutsTyp(), GRANTING_DECISIONS))
			.sorted(DECISION_ORDER.reversed())
			.map(decision -> {
				if (decision.worddokument() != null && PermitFiles.isTemplate(decision.mall(), CERTIFICATE_TEMPLATES)) {
					return new Certificate(decision, decision.worddokument(), DOCUMENTS);
				}
				if (PermitFiles.isCertificate(decision.bifogadfil())) {
					return new Certificate(decision, decision.bifogadfil(), ATTACHMENTS);
				}
				return null;
			})
			.filter(Objects::nonNull)
			.findFirst();
	}

	/**
	 * @return the events of the place without an errand; an event whose errand is missing in Ärende counts as one without
	 *         an errand, as in AlkT, so that its text and files are not lost
	 */
	private Stream<EventRow> eventsWithoutErrand(final Integer fsId) {
		return eventsByPlace.getOrDefault(fsId, List.of()).stream().filter(event -> !hasErrand(event));
	}

	/**
	 * @return true for an event whose errand is in Ärende
	 */
	private boolean hasErrand(final EventRow event) {
		return event.arendeId() != null && errandsById.containsKey(event.arendeId());
	}

	/**
	 * @return true for an event that registers a product the place has a notification of; its files go to that
	 *         notification
	 */
	private static boolean registersNotification(final SalesPlaceRow place, final EventRow event) {
		return place != null && !place.upphort() && Arrays.stream(Product.values())
			.anyMatch(product -> product.isNotifiedOn(place) && product.concerns(event));
	}

	private Candidate notification(final SalesPlaceRow place, final Product product) {
		final var hits = new ArrayList<RuleHit>();
		final var owner = ownerOf(place);
		ownerHits(place, owner, hits);
		final var registrations = registrations(place, product, owner);
		final var notified = notifiedDate(place, product, registrations, hits);
		openErrandHits(registrations.errands(), hits);
		formerHolderHits(registrations, hits);

		final var current = registrations.ofCurrentHolder();
		final var type = product.errandType(isServing(place, product, current.errands(), current.events()));
		final var diaryNumber = latestDiaryNumber(current.errands(), current.events());
		final var name = Optional.ofNullable(place.namn()).orElse(UNKNOWN_SALES_PLACE);
		final var subject = "Anmälan om " + productText(type);
		final var premises = place(place);
		final var ownEvents = notificationOwnEvents(place, product);
		final var files = notificationFiles(product, registrations, ownEvents);
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
			.title(Texts.title(subject, name))
			.description(Texts.join("\n", Arrays.asList(
				subject + " för " + name + ", migrerad från " + ALKOL.getDisplayName() + ".",
				line("Anmäld", notified.text()),
				line("Diarienummer", diaryNumber))))
			.labelPaths(properties.errandType(type).labels())
			.externalTag(TAG_NOTIFICATION, notificationId(place, product.code()))
			.externalTag(TAG_DIARY_NUMBER, diaryNumber)
			.parameters(new ParameterList()
				.add(ErrandParameters.ALKOL_FS_ID, "Id i AlkOL", String.valueOf(place.fsId()))
				.add("produkt", "Produkt", product.parameterText())
				.add("anmald", "Anmäld", SourceValues.format(notified.date()))
				.add("anmaldUppskattat", "Datumet är uppskattat", notified.estimated() ? "Ja" : null)
				.add(ErrandParameters.DIARY_NUMBER, "Diarienummer i AlkOL", diaryNumber)
				.add(ErrandParameters.NOTES, "Noteringar i AlkOL", registrations.errands().stream().map(ErrandRow::noteringar).filter(Objects::nonNull).distinct().toList())
				.add(ErrandParameters.EVENT_LOG, "Händelselogg i AlkOL", Journal.lines(notificationLog(registrations, ownEvents)))
				.add(CandidateTexts.migrated(today, ALKOL))
				.build())
			.asset(new AssetData(null, subject + " på " + name, notified.date(), null, assetParameters))
			.files(files.build(), files.assetFiles(), files.details())
			.build();
	}

	/**
	 * @return the events of the place without an errand whose files and texts go to the notification: those that register
	 *         or confirm the product, and, when the place has no tobacco permit, those that concern no notification
	 *         (decision 37)
	 */
	private List<EventRow> notificationOwnEvents(final SalesPlaceRow place, final Product product) {
		return eventsWithoutErrand(place.fsId())
			.filter(event -> product.concerns(event) || (!hasPermit(place.fsId()) && !registersNotification(place, event)))
			.toList();
	}

	/**
	 * The files of the notification: those of its errands and of its own events without an errand (decision 37). The
	 * drawings, the notification itself, which is an attachment of an event that registers the product, and the letters
	 * that confirm it, from its errands and the events that register or confirm the product, also go on the post in
	 * PartyAssets: the drawings in the category of their purpose, the others without one, as pw-alkt puts a file without a
	 * purpose there (decisions 38 and 40). Those of a former holder's registrations stay on the errand only (G31), as does
	 * anything else, such as an investigation, a referral answer or a file of the place's other events.
	 */
	private FileList notificationFiles(final Product product, final Registrations registrations, final List<EventRow> ownEvents) {
		final var files = addErrandFiles(registrations.errands().stream().map(ErrandRow::arendeId).toList(), new FileList(sharedFileNames));
		ownEvents.forEach(event -> addEventFiles(files, event));

		// Marked first, since a file marked for the errand only never goes on the post
		final var former = registrations.ofFormerHolders();
		postFileNames(product, former).forEach(file -> files.addToErrandOnly(file.name(), file.folder()));

		final var current = registrations.ofCurrentHolder();
		final var events = postEvents(current);
		final var decisions = decisionsOf(current.errands());
		// The drawings first, since a file keeps the category it is first marked with
		Stream.concat(events.stream().filter(event -> FilePurposes.isDrawing(attachmentPurpose(event))).map(EventRow::bifogadfil),
			decisions.stream().map(DecisionRow::bifogadfil).filter(PermitFiles::isDrawing))
			.forEach(name -> files.add(name, ATTACHMENTS, ASSET_CATEGORY_DRAWING));
		postFileNames(product, current).forEach(file -> files.addToPost(file.name(), file.folder()));
		return files;
	}

	/**
	 * @return the notifications themselves and the letters that confirm them, among the files of the registrations and
	 *         their errands, and the drawings
	 */
	private List<FileReference> postFileNames(final Product product, final Registrations registrations) {
		final var events = postEvents(registrations);
		final var decisions = decisionsOf(registrations.errands());
		return Stream.of(
			events.stream().filter(event -> FilePurposes.isDrawing(attachmentPurpose(event))).flatMap(event -> reference(event.bifogadfil(), ATTACHMENTS)),
			decisions.stream().map(DecisionRow::bifogadfil).filter(PermitFiles::isDrawing).flatMap(name -> reference(name, ATTACHMENTS)),
			events.stream().filter(product::concerns).flatMap(event -> reference(event.bifogadfil(), ATTACHMENTS)),
			events.stream().filter(event -> product.isConfirmationTemplate(event.mall())).flatMap(event -> reference(event.worddokument(), DOCUMENTS)),
			decisions.stream().filter(decision -> product.isConfirmationTemplate(decision.mall())).flatMap(decision -> reference(decision.worddokument(), DOCUMENTS)))
			.flatMap(references -> references)
			.toList();
	}

	private static Stream<FileReference> reference(final String name, final FileFolder folder) {
		return Stream.ofNullable(Texts.trimToNull(name)).map(trimmed -> new FileReference(trimmed, folder));
	}

	/**
	 * @return the events of the errands of the registrations, and the registrations' events without one of those errands
	 */
	private List<EventRow> postEvents(final Registrations registrations) {
		final var arendeIds = registrations.errands().stream().map(ErrandRow::arendeId).collect(toSet());
		return Stream.concat(arendeIds.stream().flatMap(arendeId -> eventsByErrand.getOrDefault(arendeId, List.of()).stream()),
			registrations.events().stream().filter(event -> !arendeIds.contains(event.arendeId())))
			.distinct()
			.toList();
	}

	private List<DecisionRow> decisionsOf(final List<ErrandRow> errands) {
		return errands.stream().flatMap(errand -> decisionsByErrand.getOrDefault(errand.arendeId(), List.of()).stream()).toList();
	}

	/**
	 * @return the texts of the notification's errands and events, for its event log: the events and decisions of its
	 *         errands and the place's own events that go to it, as the files do; a notification has no investigation
	 */
	private List<Journal.Entry> notificationLog(final Registrations registrations, final List<EventRow> ownEvents) {
		final var errandEntries = registrations.errands().stream().flatMap(errand -> Stream.concat(
			eventsByErrand.getOrDefault(errand.arendeId(), List.of()).stream().filter(event -> event.text() != null).map(event -> journalEntry(event, errand.diarieNr())),
			decisionsByErrand.getOrDefault(errand.arendeId(), List.of()).stream().filter(decision -> decision.beslutsText() != null)
				.map(decision -> new Journal.Entry(decision.beslutad(),
					Journal.decisionHeading(CodeTexts.textOf(snapshot.decisionTypes(), decision.beslutsTyp()), CodeTexts.textOf(snapshot.userNames(), decision.beslutsfattare())),
					decision.beslutsText()))));
		final var ownEntries = ownEvents.stream().filter(event -> event.text() != null).map(event -> journalEntry(event, null));
		return Stream.concat(errandEntries, ownEntries).toList();
	}

	/**
	 * @return the registrations of the product on the place: its errands, and its events that register or confirm it,
	 *         with or without an errand. An errand whose applicant has another number than the owner is a former
	 *         holder's (as G19 compares them). Then what came before the first errand of the owner is a former holder's
	 *         too, or, without one, what came up to the day the last former holder's errand closed; an event of an errand
	 *         goes with its errand, and an errand whose applicant is the owner is never a former holder's.
	 */
	private Registrations registrations(final SalesPlaceRow place, final Product product, final ContactRow owner) {
		final var errands = errandsByPlace.getOrDefault(place.fsId(), List.of()).stream()
			.filter(errand -> product.isErrandType(errand.arendetyp()))
			.toList();
		final var events = eventsByPlace.getOrDefault(place.fsId(), List.of()).stream()
			.filter(product::concerns)
			.toList();
		final var formerEnd = errands.stream()
			.filter(errand -> applicantIsNotOwner(errand, owner))
			.map(errand -> Optional.ofNullable(errand.avslutad()).orElse(errandDate(errand)))
			.filter(Objects::nonNull)
			.max(Comparator.naturalOrder());
		final var ownerStart = errands.stream()
			.filter(errand -> applicantIsOwner(errand, owner))
			.map(AlkolCandidateFactory::errandDate)
			.filter(Objects::nonNull)
			.min(Comparator.naturalOrder());
		final var formerUntil = formerEnd.map(end -> ownerStart.map(start -> start.minusDays(1)).orElse(end)).orElse(null);
		final var formerIds = errands.stream()
			.filter(errand -> applicantIsNotOwner(errand, owner) || (!applicantIsOwner(errand, owner) && isUpTo(errandDate(errand), formerUntil)))
			.map(ErrandRow::arendeId)
			.collect(toSet());
		final var errandIds = errands.stream().map(ErrandRow::arendeId).collect(toSet());
		final var formerEvents = events.stream()
			.filter(event -> errandIds.contains(event.arendeId()) ? formerIds.contains(event.arendeId()) : isUpTo(dateOf(event), formerUntil))
			.collect(toSet());
		return new Registrations(errands, events, formerIds, formerEvents);
	}

	/**
	 * G31 when a former holder registered the product, whose files stay on the errand only, and G19 when no registration is
	 * the current owner's.
	 */
	private static void formerHolderHits(final Registrations registrations, final List<RuleHit> hits) {
		final var former = registrations.ofFormerHolders();
		if (former.errands().isEmpty() && former.events().isEmpty()) {
			return;
		}
		hits.add(RuleHit.of(NOTIFICATION_OF_FORMER_HOLDER, Texts.join(", ", former.errands().stream().map(errand -> CandidateTexts.errand(errand.arendeId(), errand.diarieNr())).toList())));
		final var current = registrations.ofCurrentHolder();
		if (current.errands().isEmpty() && current.events().isEmpty()) {
			hits.add(RuleHit.of(APPLICANT_NOT_OWNER, "anmälan är gjord av en tidigare ägare"));
		}
	}

	/**
	 * @return true when the applicant of the errand is the current owner of the place, both with a number that can be read
	 */
	private boolean applicantIsOwner(final ErrandRow errand, final ContactRow owner) {
		final var applicantNumber = Optional.ofNullable(errand).map(ErrandRow::sokandeId).map(snapshot.applicants()::get)
			.flatMap(applicant -> PartyRows.numberKey(applicant.organisationsNr(), today));
		final var ownerNumber = Optional.ofNullable(owner).flatMap(found -> PartyRows.numberKey(found.organisationsNr(), today));
		return applicantNumber.isPresent() && applicantNumber.equals(ownerNumber);
	}

	private static boolean isUpTo(final LocalDate date, final LocalDate until) {
		return date != null && until != null && !date.isAfter(until);
	}

	/**
	 * @return the day the errand was opened, or else registered (UpplagdDatum); null when it has neither
	 */
	private static LocalDate errandDate(final ErrandRow errand) {
		return Optional.ofNullable(errand.oppnad()).orElse(errand.upplagd());
	}

	private static LocalDate dateOf(final EventRow event) {
		return event.tidpunkt() == null ? null : event.tidpunkt().toLocalDate();
	}

	private Candidate inspectionErrand(final ErrandRow errand, final InspectionCandidates inspections) {
		final var place = errand.fsId() == null ? null : placesById.get(errand.fsId());
		final var source = new InspectionErrand(ALKOL, errand.arendeId(), errand.arendetyp(), CodeTexts.textOf(snapshot.errandTypes(), errand.arendetyp()), errand.diarieNr(), errand.oppnad(),
			errand.avslutad(), snapshot.visits().handlerNames(errand.handlaggare()), errand.upplagd(), latestActivity(errand.arendeId()));
		final var applicant = errand.sokandeId() == null ? null : snapshot.applicants().get(errand.sokandeId());
		final var decisions = decisionsByErrand.getOrDefault(errand.arendeId(), List.of()).stream()
			.sorted(DECISION_ORDER)
			.map(this::decisionText)
			.filter(Objects::nonNull)
			.toList();

		final var files = addErrandFiles(List.of(errand.arendeId()), new FileList(sharedFileNames));
		return inspections.builder(source, place == null ? null : place(place), applicant, ownerOf(place), placePermit(errand.fsId()), decisions, investigationTexts(errand))
			.files(files.build(), files.assetFiles(), files.details())
			.build();
	}

	/**
	 * @return the time of the errand's latest event or decision, null when it has none with a time
	 */
	private LocalDateTime latestActivity(final Integer arendeId) {
		return Stream.concat(eventsByErrand.getOrDefault(arendeId, List.of()).stream().map(EventRow::tidpunkt),
			decisionsByErrand.getOrDefault(arendeId, List.of()).stream().map(DecisionRow::beslutad))
			.filter(Objects::nonNull)
			.max(Comparator.naturalOrder())
			.orElse(null);
	}

	private Candidate visit(final VisitRow visit, final InspectionVisitCandidates visits) {
		final var place = visit.objektId() == null ? null : placesById.get(visit.objektId());
		final var files = new FileList(sharedFileNames);
		snapshot.visits().forEachFile(visit, (name, folder) -> files.add(name, folder, FilePurposes.ofVisit(ALKOL, folder, name, SourceValues.startOfDay(visit.datum()))));
		return visits.builder(visit, snapshot.visits(), place == null ? null : place(place), placePermit(visit.objektId()))
			.files(files.build(), files.assetFiles(), files.details())
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
	 * The rules about the granting decision: G15, G18, G9 for a later grant without a date, G16 and G19.
	 */
	private void grantHits(final Integer fsId, final DecisionRow grant, final ErrandRow grantErrand, final ContactRow owner, final List<RuleHit> hits) {
		if (grant == null) {
			hits.add(RuleHit.of(DIARY_NUMBER_MISSING, "inget beviljande beslut hittades"));
			return;
		}
		endingAfter(fsId, grant).ifPresent(ending -> hits.add(RuleHit.of(PERMIT_ENDED, Texts.joinWords(ending.beslutsTyp(), dateText(ending)))));
		decisionsOf(fsId).stream()
			.filter(decision -> decision.beslutad() == null && CodeTexts.isOneOf(decision.beslutsTyp(), GRANTING_DECISIONS) && isAfter(decision, grant))
			.findFirst()
			.ifPresent(undated -> hits.add(RuleHit.of(UNPARSEABLE, "det beviljande beslutet %d saknar datum och kan vara det senaste".formatted(undated.beslutsId()))));
		final var diaryNumber = grantErrand == null ? null : DiaryNumbers.normalize(grantErrand.diarieNr());
		if (diaryNumber == null) {
			hits.add(RuleHit.of(DIARY_NUMBER_MISSING, "det beviljande ärendet saknar diarienummer"));
		} else if (permitsByDiaryNumber.getOrDefault(diaryNumber, 0) > 1) {
			hits.add(RuleHit.of(DIARY_NUMBER_ON_SEVERAL, grantErrand.diarieNr()));
		}
		if (applicantIsNotOwner(grantErrand, owner)) {
			hits.add(RuleHit.of(APPLICANT_NOT_OWNER));
		}
	}

	/**
	 * @return the latest decision that ended the permit after it was granted (G18), also one without a date that was
	 *         entered after the grant; empty when there is none or no grant
	 */
	private Optional<DecisionRow> endingAfter(final Integer fsId, final DecisionRow grant) {
		return grant == null ? Optional.empty()
			: decisionsOf(fsId).stream()
				.filter(decision -> CodeTexts.isOneOf(decision.beslutsTyp(), ENDING_DECISIONS))
				.filter(decision -> isAfter(decision, grant))
				.max(DECISION_ORDER);
	}

	/**
	 * @return true when the decision came after the other: by their dates, or by their ids, which follow the order the
	 *         decisions were entered in, when either has no date
	 */
	private static boolean isAfter(final DecisionRow decision, final DecisionRow other) {
		if (decision.beslutad() == null || other.beslutad() == null) {
			return Comparator.nullsFirst(Comparator.<Integer>naturalOrder()).compare(decision.beslutsId(), other.beslutsId()) > 0;
		}
		return DECISION_ORDER.compare(decision, other) > 0;
	}

	private static String dateText(final DecisionRow decision) {
		return Optional.ofNullable(SourceValues.format(decision.beslutsDatum())).orElse(UNDATED);
	}

	/**
	 * @return true when the applicant of the granting errand is not the current owner of the place, both with a number
	 *         that can be read (G19)
	 */
	private boolean applicantIsNotOwner(final ErrandRow grantErrand, final ContactRow owner) {
		final var applicantNumber = Optional.ofNullable(grantErrand).map(ErrandRow::sokandeId).map(snapshot.applicants()::get)
			.flatMap(applicant -> PartyRows.numberKey(applicant.organisationsNr(), today));
		final var ownerNumber = Optional.ofNullable(owner).flatMap(found -> PartyRows.numberKey(found.organisationsNr(), today));
		return applicantNumber.isPresent() && ownerNumber.isPresent() && !applicantNumber.equals(ownerNumber);
	}

	/**
	 * @return the current tobacco permit of the sales place, which its inspections are related to (decision 61); null when
	 *         the place has none
	 */
	private PlacePermit placePermit(final Integer fsId) {
		if (!hasPermit(fsId)) {
			return null;
		}
		final var grant = latestGrant(fsId).orElse(null);
		final var grantErrand = grant == null ? null : errandsById.get(grant.arendeId());
		return new PlacePermit(new ItemKey(ALKOL, TILLSTAND, String.valueOf(fsId)), issued(grant), endingAfter(fsId, grant).map(DecisionRow::beslutsDatum).orElse(null),
			placesById.containsKey(fsId) && !applicantIsNotOwner(grantErrand, ownerOf(placesById.get(fsId))));
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
	private NotifiedDate notifiedDate(final SalesPlaceRow place, final Product product, final Registrations registrations, final List<RuleHit> hits) {
		if (registrations.errands().isEmpty() && registrations.events().isEmpty()) {
			hits.add(RuleHit.of(NOTIFICATION_ONLY_FLAG, product.parameterText()));
			final var earliest = earliestDate(place.fsId()).orElse(null);
			if (earliest == null) {
				hits.add(RuleHit.of(NOTIFICATION_DATE_MISSING, product.parameterText()));
			}
			return new NotifiedDate(earliest, earliest != null);
		}
		// The current holder's first registration, or the first of all when every one is a former holder's (G19)
		final var current = registrations.ofCurrentHolder();
		final var dated = current.errands().isEmpty() && current.events().isEmpty() ? registrations : current;
		final var first = Stream.concat(dated.errands().stream().map(AlkolCandidateFactory::errandDate), dated.events().stream().map(AlkolCandidateFactory::dateOf))
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
			.max(Comparator.comparing(AlkolCandidateFactory::errandDate, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(ErrandRow::arendeId))
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
			.filter(decision -> CodeTexts.isOneOf(decision.beslutsTyp(), GRANTING_DECISIONS))
			.filter(decision -> isAfter(grant, decision))
			.sorted(DECISION_ORDER)
			.map(decision -> Texts.join(", ", Arrays.asList(dateText(decision), Optional.ofNullable(errandsById.get(decision.arendeId())).map(ErrandRow::diarieNr).orElse(null))))
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
			.filter(decision -> isAfter(decision, grant))
			.max(DECISION_ORDER)
			.map(this::decisionText)
			.orElse(null);
	}

	/**
	 * @return the texts of the investigation of the errand: its notes, its events, and its decisions with their texts and
	 *         who made them (decisions 44, 46, 52 and 53)
	 */
	private InvestigationTexts investigationTexts(final ErrandRow errand) {
		return investigationTexts(errand, null);
	}

	/**
	 * @param  shownNote a note the errand already shows as a parameter, which the investigation leaves out; null when none
	 * @return           the texts of the investigation of the errand, as above, without the note already shown
	 */
	private InvestigationTexts investigationTexts(final ErrandRow errand, final String shownNote) {
		return Journal.investigation(
			Stream.ofNullable(errand.noteringar()).filter(note -> !note.equals(shownNote)).toList(),
			eventsByErrand.getOrDefault(errand.arendeId(), List.of()).stream().map(event -> journalEntry(event, errand.diarieNr())).toList(),
			decisionsByErrand.getOrDefault(errand.arendeId(), List.of()).stream()
				.map(decision -> new Journal.Entry(decision.beslutad(),
					Journal.decisionHeading(CodeTexts.textOf(snapshot.decisionTypes(), decision.beslutsTyp()), CodeTexts.textOf(snapshot.userNames(), decision.beslutsfattare())),
					decision.beslutsText()))
				.toList());
	}

	/**
	 * @param  errandDiaryNumber the diary number of the errand of the event, null for an event without an errand
	 * @return                   the event as an entry of a journal, headed by the text of its type, marked when
	 *                           confidential and with its own diary number when it is another than the errand's
	 */
	private Journal.Entry journalEntry(final EventRow event, final String errandDiaryNumber) {
		return new Journal.Entry(event.tidpunkt(),
			Journal.eventHeading(CodeTexts.textOf(snapshot.eventTypes(), event.handelseTyp()), event.confidential(), Journal.otherDiaryNumber(event.diarieNr(), errandDiaryNumber)),
			event.text());
	}

	private String decisionText(final DecisionRow decision) {
		return Texts.join(", ", Arrays.asList(CodeTexts.textOf(snapshot.decisionTypes(), decision.beslutsTyp()), SourceValues.format(decision.beslutsDatum())));
	}

	/**
	 * @return true when the place is in the view FS_GT_Tobak, also when a later decision ended its permit (G18)
	 */
	private boolean hasPermit(final Integer fsId) {
		return fsId != null && snapshot.permitView().containsKey(fsId);
	}

	/**
	 * @return true when the place is in the view FS_GT_Tobak and no decision ended its permit after its latest grant
	 */
	private boolean hasCurrentPermit(final Integer fsId) {
		return hasPermit(fsId) && endingAfter(fsId, latestGrant(fsId).orElse(null)).isEmpty();
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
			.add(ErrandParameters.AREA, "Område", area)
			.build());
	}

	private List<DecisionRow> decisionsOf(final Integer fsId) {
		return decisionsByPlace.getOrDefault(fsId, List.of());
	}

	private Optional<DecisionRow> latestGrant(final Integer fsId) {
		return decisionsOf(fsId).stream()
			.filter(decision -> CodeTexts.isOneOf(decision.beslutsTyp(), GRANTING_DECISIONS))
			.max(DECISION_ORDER);
	}

	/**
	 * The kinds of permit, which give both its type and its kinds of sale on the post: the columns of the view, or the
	 * type of the latest granting decision when the view shows none.
	 */
	private static List<PermitKind> permitKinds(final PermitViewRow view, final DecisionRow grant) {
		final var kinds = Arrays.stream(PermitKind.values())
			.filter(kind -> view != null && kind.isInView(view))
			.toList();
		if (!kinds.isEmpty() || grant == null) {
			return kinds;
		}
		return Arrays.stream(PermitKind.values())
			.filter(kind -> kind.decision().equalsIgnoreCase(Optional.ofNullable(grant.beslutsTyp()).map(String::strip).orElse("")))
			.toList();
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
	 * Whether a notification of folköl is about serving: the latest notification of folköl decides, an errand or an event
	 * that registers one, not one that confirms it; otherwise the category of the sales place (section 7.4).
	 */
	private boolean isServing(final SalesPlaceRow place, final Product product, final List<ErrandRow> errands, final List<EventRow> events) {
		record Registration(LocalDateTime when, boolean serving) {
		}
		final var latest = Stream.concat(
			errands.stream().map(errand -> new Registration(Optional.ofNullable(errandDate(errand)).map(LocalDate::atStartOfDay).orElse(null), product.isServingCode(errand.arendetyp()))),
			events.stream().filter(product::isRegistration).map(event -> new Registration(event.tidpunkt(), product.isServingCode(event.handelseTyp()))))
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
			errandsByPlace.getOrDefault(fsId, List.of()).stream().map(AlkolCandidateFactory::errandDate),
			eventsByPlace.getOrDefault(fsId, List.of()).stream().map(AlkolCandidateFactory::dateOf))
			.filter(Objects::nonNull)
			.min(Comparator.naturalOrder());
	}

	private FileList addErrandFiles(final Collection<Integer> arendeIds, final FileList files) {
		for (final var arendeId : arendeIds) {
			eventsByErrand.getOrDefault(arendeId, List.of()).forEach(event -> addEventFiles(files, event));
			decisionsByErrand.getOrDefault(arendeId, List.of()).forEach(decision -> files
				.add(decision.worddokument(), DOCUMENTS, FilePurposes.ofGenerated(ALKOL, decision.mall(), null, decision.beslutad()))
				.add(decision.bifogadfil(), ATTACHMENTS, FilePurposes.detailsOf(ALKOL, ATTACHMENTS, decision.bifogadfil(), decision.beslutad())));
		}
		return files;
	}

	/**
	 * Adds the document and the attachment of the event, each with its purpose and the time of the event.
	 */
	private static FileList addEventFiles(final FileList files, final EventRow event) {
		return files
			.add(event.worddokument(), DOCUMENTS, FilePurposes.ofGenerated(ALKOL, event.mall(), event.handelseTyp(), event.tidpunkt()))
			.add(event.bifogadfil(), ATTACHMENTS, FilePurposes.ofAttached(ALKOL, event.handelseTyp(), event.bifogadfil(), event.tidpunkt()));
	}

	private static String attachmentPurpose(final EventRow event) {
		return FilePurposes.ofAttachment(ALKOL, event.handelseTyp(), event.bifogadfil());
	}

	/**
	 * Every file reference of AlkOL with the sales place it belongs to: the place of the errand it goes to, or the errand
	 * itself when the errand has no place. An event without an errand, also one whose errand is missing (decision 54),
	 * belongs to its own place, or is a place of its own.
	 */
	private SharedFileNames sharedFileNames() {
		final var names = SharedFileNames.builder(properties.fileArea(ALKOL));
		snapshot.events().forEach(event -> {
			final var withoutErrand = event.fsId() != null ? "fs:" + event.fsId() : "handelse:" + event.handelseId();
			final var place = hasErrand(event) ? placeKeyOfErrand(event.arendeId(), withoutErrand) : withoutErrand;
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
	 * The parameters of the tobacco permit, under the keys of the AoT solution (section 7.4). The kinds of sale are the
	 * kinds of the permit.
	 */
	private static Map<String, String> permitAssetParameters(final Holder holder, final Place premises, final List<PermitKind> kinds, final String diaryNumber) {
		final var parameters = new AssetParameters()
			.holder(holder)
			.premises(premises, null);
		Arrays.stream(PermitKind.values()).forEach(kind -> parameters.flag(kind.salesParameter(), kinds.contains(kind)));
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
	 * The certificate of a tobacco permit.
	 *
	 * @param decision the granting decision it belongs to
	 * @param name     the file name
	 * @param folder   the directory of the file area
	 */
	private record Certificate(DecisionRow decision, String name, FileFolder folder) {

		FileDetails details() {
			return folder == DOCUMENTS
				? FilePurposes.ofGenerated(ALKOL, decision.mall(), null, decision.beslutad())
				: FilePurposes.detailsOf(ALKOL, folder, name, decision.beslutad());
		}
	}

	/**
	 * The registrations of a product on a sales place.
	 *
	 * @param errands      the errands of the product's types
	 * @param events       the events that register or confirm the product, with or without an errand
	 * @param formerIds    the ArendeID of the errands of former holders
	 * @param formerEvents the events of former holders
	 */
	private record Registrations(List<ErrandRow> errands, List<EventRow> events, Set<Integer> formerIds, Set<EventRow> formerEvents) {

		Registrations ofCurrentHolder() {
			return new Registrations(errands.stream().filter(errand -> !formerIds.contains(errand.arendeId())).toList(),
				events.stream().filter(event -> !formerEvents.contains(event)).toList(), Set.of(), Set.of());
		}

		Registrations ofFormerHolders() {
			return new Registrations(errands.stream().filter(errand -> formerIds.contains(errand.arendeId())).toList(),
				events.stream().filter(formerEvents::contains).toList(), formerIds, formerEvents);
		}
	}

	/**
	 * The date of a notification.
	 *
	 * @param date      the date, null when unknown
	 * @param estimated true when the date is the earliest date of the sales place rather than of the notification
	 */
	private record NotifiedDate(LocalDate date, boolean estimated) {

		/**
		 * @return the date as "ÅÅÅÅ-MM-DD", followed by " (uppskattat)" when it is estimated, or null when it is unknown
		 */
		String text() {
			if (date == null) {
				return null;
			}
			return SourceValues.format(date) + (estimated ? " (uppskattat)" : "");
		}
	}

	/**
	 * The kinds of tobacco permit: the column of the view FS_GT_Tobak and the type of the granting decision that give the
	 * kind, its text, and the parameter of the post that tells its kind of sale (section 7.4).
	 */
	enum PermitKind {

		RETAIL("GTDE", "Detaljhandel", AssetParameters.SALES_RETAIL),
		WHOLESALE("GTPH", "Partihandel", AssetParameters.SALES_WHOLESALE),
		DISTANCE("GTDI", "Distanshandel", AssetParameters.SALES_ONLINE);

		private final String decision;
		private final String text;
		private final String salesParameter;

		PermitKind(final String decision, final String text, final String salesParameter) {
			this.decision = decision;
			this.text = text;
			this.salesParameter = salesParameter;
		}

		String decision() {
			return decision;
		}

		String text() {
			return text;
		}

		String salesParameter() {
			return salesParameter;
		}

		boolean isInView(final PermitViewRow view) {
			return switch (this) {
				case RETAIL -> view.retail();
				case WHOLESALE -> view.wholesale();
				case DISTANCE -> view.distance();
			};
		}
	}

	/**
	 * The products that are notified, with the errand types and event types that register a notification, and the event
	 * types and templates of the letters that confirm one (section 7.4, decisions 21 and 38).
	 */
	enum Product {

		FOLKOL("FOLKOL", "Folköl", Set.of("Fföl", "SÖL"), Set.of("014", "015"), Set.of("BeaFol"), Set.of("BEKFOL", "MOTFOL"), Set.of("SÖL", "015")),
		ECIGARETTER("ECIGARETTER", "E-cigaretter och påfyllningsbehållare", Set.of("ec"), Set.of("105"), Set.of("BekAnm"), Set.of("BEKECI"), Set.of()),
		TOBAKSFRI_NIKOTIN("TOBAKSFRI_NIKOTIN", "Tobaksfria nikotinprodukter", Set.of("tn"), Set.of("047"), Set.of(), Set.of("MTN"), Set.of());

		private final String code;
		private final String parameterText;
		private final Set<String> errandTypes;
		private final Set<String> registrationEvents;
		private final Set<String> confirmationEvents;
		private final Set<String> confirmationTemplates;
		private final Set<String> servingCodes;

		/**
		 * @param confirmationTemplates the templates of the letters that confirm a notification of the product, in upper
		 *                              case: the confirmations and the receipts (decision 38)
		 */
		Product(final String code, final String parameterText, final Set<String> errandTypes, final Set<String> registrationEvents, final Set<String> confirmationEvents,
			final Set<String> confirmationTemplates, final Set<String> servingCodes) {
			this.code = code;
			this.parameterText = parameterText;
			this.errandTypes = errandTypes;
			this.registrationEvents = registrationEvents;
			this.confirmationEvents = confirmationEvents;
			this.confirmationTemplates = confirmationTemplates;
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
			return CodeTexts.isOneOf(code, errandTypes);
		}

		/**
		 * @return true for an event that registers a notification of the product
		 */
		boolean isRegistration(final EventRow event) {
			return CodeTexts.isOneOf(event.handelseTyp(), registrationEvents);
		}

		/**
		 * @return true for an event that registers or confirms a notification of the product. A letter made from the
		 *         template of a confirmation is of the product of that template, whatever the type of its event; any
		 *         other event confirms by its type.
		 */
		boolean concerns(final EventRow event) {
			if (isRegistration(event)) {
				return true;
			}
			return ofTemplate(event.mall()).map(product -> product == this).orElseGet(() -> CodeTexts.isOneOf(event.handelseTyp(), confirmationEvents));
		}

		boolean isConfirmationTemplate(final String template) {
			return PermitFiles.isTemplate(template, confirmationTemplates);
		}

		private static Optional<Product> ofTemplate(final String template) {
			return Arrays.stream(values()).filter(product -> product.isConfirmationTemplate(template)).findFirst();
		}

		/**
		 * @return true for an errand type or event type that notifies serving rather than sales of the product
		 */
		boolean isServingCode(final String code) {
			return CodeTexts.isOneOf(code, servingCodes);
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
