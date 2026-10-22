package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.PlacePermit;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReaderResult.Notice;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.AssetParameters;
import se.sundsvall.aotloader.source.CandidateTexts;
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
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ConditionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PermitRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PlaceRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.TextRow;
import se.sundsvall.aotloader.source.files.SharedFileNames;
import se.sundsvall.aotloader.support.DiaryNumbers;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_ANNEX;
import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_CERTIFICATE;
import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_DRAWING;
import static se.sundsvall.aotloader.Constants.ASSET_PARAMETER_CONDITIONS;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_RESTAURANT_NUMBER;
import static se.sundsvall.aotloader.Constants.TAG_DIARY_NUMBER;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_OLDER;
import static se.sundsvall.aotloader.service.model.ReviewRule.DECISION_EXPIRED;
import static se.sundsvall.aotloader.service.model.ReviewRule.DECISION_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_ON_SEVERAL;
import static se.sundsvall.aotloader.service.model.ReviewRule.ISSUE_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_INVALID;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_ON_SEVERAL_OWNERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.NO_ISSUING_ERRAND;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_DIFFERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERIOD_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERIOD_ON_ALL_YEAR;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERIOD_PASSED;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERMIT_ROWS_ON_PLACE;
import static se.sundsvall.aotloader.service.model.ReviewRule.PLACE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.RESTAURANT_NUMBER_DIFFERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.RESTAURANT_NUMBER_INVALID;
import static se.sundsvall.aotloader.service.model.ReviewRule.SUBTYPE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNDERLYING_ERRAND_OPEN;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNPARSEABLE;
import static se.sundsvall.aotloader.service.model.Source.ALKT;
import static se.sundsvall.aotloader.source.CandidateTexts.UNKNOWN_SERVING_PLACE;
import static se.sundsvall.aotloader.source.CandidateTexts.line;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_AREA;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_CONDITION;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_DECISION;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_ERRAND;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_EVENT;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_PLACE_GROUP;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_POSITION;

/**
 * Turns what was read from AlkT into candidates: the current serving permits (sections 7.2–7.6), the inspection
 * errands (section 7.7) and the inspection visits (section 7.7.2), with the review rules that AlkT itself shows.
 */
public final class AlktCandidateFactory {

	private static final Set<String> PLACE_MARKINGS = Set.of("UNG", "UTOK");
	private static final String WITHOUT_ID = "utan-id-";
	/** The templates of the permit certificate: Sundsvall bevis and the one before it (decision 34). */
	private static final Set<String> CERTIFICATE_TEMPLATES = Set.of("TDBV2", "TDBV");
	/** The templates of the annex of the permit: of Sundsvall, of Timrå, or the older one (decision 34). */
	private static final Set<String> ANNEX_TEMPLATES = Set.of("TDBBS", "TBBT", "TDBB");
	/** The template of the annex for catering, which a permit with catering gets besides the other (decision 34). */
	private static final Set<String> CATERING_ANNEX_TEMPLATES = Set.of("TDBBC");
	/** The templates of the permit decision: of Sundsvall and of Timrå (decision 39). */
	private static final Set<String> DECISION_TEMPLATES = Set.of("TDB", "TBT");
	private static final String PICTURE_HEADING = "Bild";
	private static final Comparator<PlaceDocument> DOCUMENT_ORDER = Comparator
		.comparing(PlaceDocument::tidpunkt, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
		.thenComparing(PlaceDocument::order, Comparator.nullsFirst(Comparator.<Integer>naturalOrder()));

	private final AlktSnapshot snapshot;
	private final LocalDate today;
	private final AotLoaderProperties properties;
	private final Map<String, Set<Integer>> ownersByNumber = new HashMap<>();
	private final Map<String, Integer> permitsByDiaryNumber = new HashMap<>();
	private final Map<Integer, List<PermitRow>> permitRowsByPlace = new HashMap<>();
	private final Map<Integer, List<DecisionRow>> decisionsByErrand = new HashMap<>();
	private final Map<Integer, List<PlaceDocument>> documentsByPlace = new HashMap<>();
	private final SharedFileNames sharedFileNames;

	public AlktCandidateFactory(final AlktSnapshot snapshot, final LocalDate today, final AotLoaderProperties properties) {
		this.snapshot = snapshot;
		this.today = today;
		this.properties = properties;
		this.sharedFileNames = sharedFileNames(snapshot, properties);
		// G16 counts places, so that the rows of one place (G26) with the same diary number count once
		final var diaryNumbersOfPlaces = new HashSet<String>();
		snapshot.permits().forEach(permit -> {
			Optional.ofNullable(permit.objektId()).ifPresent(objektId -> permitRowsByPlace.computeIfAbsent(objektId, _ -> new ArrayList<>()).add(permit));
			Optional.ofNullable(DiaryNumbers.normalize(permit.utfardandeDiarienr()))
				.filter(number -> permit.objektId() == null || diaryNumbersOfPlaces.add(number + "/" + permit.objektId()))
				.ifPresent(number -> permitsByDiaryNumber.merge(number, 1, Integer::sum));
			ownerOf(placeOf(permit.objektId()))
				.ifPresent(owner -> PartyRows.numberKey(owner.organisationsNr(), today)
					.ifPresent(number -> ownersByNumber.computeIfAbsent(number, _ -> new HashSet<>()).add(owner.id())));
		});
		snapshot.decisions().values().stream()
			.filter(decision -> decision.arendeId() != null)
			.forEach(decision -> decisionsByErrand.computeIfAbsent(decision.arendeId(), _ -> new ArrayList<>()).add(decision));
		indexDocuments();
	}

	/**
	 * @return the current serving permits, one per place, with a notice for each permit that has no ObjektID and therefore
	 *         no id. A place with several rows (G26) is one item, built from its first row, since the rows would share
	 *         their key.
	 */
	public ReaderResult permits() {
		final var candidates = new ArrayList<Candidate>();
		final var notices = new ArrayList<Notice>();
		final var places = new HashSet<Integer>();
		var withoutId = 0;
		for (final var permit : snapshot.permits()) {
			if (permit.objektId() == null) {
				withoutId++;
				notices.add(new Notice(new ItemKey(ALKT, TILLSTAND, WITHOUT_ID + withoutId), null, Outcome.GRANSKAS, PLACE_MISSING.getCode(),
					PLACE_MISSING.getCode() + " Tillståndet saknar ObjektID, diarienummer " + Optional.ofNullable(permit.utfardandeDiarienr()).orElse("saknas")));
			} else if (places.add(permit.objektId())) {
				candidates.add(permit(permit));
			}
		}
		return new ReaderResult(candidates, notices);
	}

	/**
	 * @return the inspection errands of the errand types in the settings, only of places with a current permit when the
	 *         settings say so
	 */
	public ReaderResult inspections() {
		final var inspections = new InspectionCandidates(ErrandType.ALCOHOL_INSPECTION, properties, UNKNOWN_SERVING_PLACE, "tillsynsärendet saknar serveringsställe", today);
		final var candidates = snapshot.errands().values().stream()
			.filter(errand -> inspections.selects(errand.arendeTyp(), permitRowsByPlace.containsKey(errand.objektId())))
			.map(errand -> inspectionErrand(errand, inspections))
			.toList();
		return new ReaderResult(candidates, List.of());
	}

	/**
	 * @return the inspection visits, only of places with a current permit when the settings say so
	 */
	public ReaderResult visits() {
		final var visits = new InspectionVisitCandidates(ErrandType.ALCOHOL_INSPECTION_VISIT, properties, UNKNOWN_SERVING_PLACE, "tillsynsbesöket saknar serveringsställe", today);
		final var candidates = snapshot.visits().visits().stream()
			.filter(visit -> visits.selects(permitRowsByPlace.containsKey(visit.objektId())))
			.map(visit -> visit(visit, visits))
			.toList();
		return new ReaderResult(candidates, List.of());
	}

	private Candidate permit(final PermitRow permit) {
		final var place = placeOf(permit.objektId());
		final var owner = ownerOf(place).orElse(null);
		final var holder = PartyRows.holder(owner, today);
		final var hits = new ArrayList<RuleHit>();
		placeAndOwnerHits(permit, place, owner, holder, hits);
		final var decision = decisionOf(permit, hits);
		final var periods = PermitPeriods.of(permit);
		periodHits(permit, periods, hits);
		final var content = new PermitContent(permit, periods, servingTimes(permit, hits), conditions(permit.objektId(), hits));
		final var issuingErrands = issuingErrands(permit);
		if (issuingErrands.isEmpty()) {
			hits.add(RuleHit.of(NO_ISSUING_ERRAND));
		} else if (issuingErrands.stream().noneMatch(snapshot.errands()::containsKey)) {
			hits.add(RuleHit.of(NO_ISSUING_ERRAND, "ArendeID %s finns inte i Ärende".formatted(Texts.join(", ", issuingErrands.stream().map(String::valueOf).toList()))));
		}
		openErrandHits(issuingErrands, hits);
		diaryNumberHits(permit, hits);
		if (permit.utfardandeDatum() == null) {
			hits.add(RuleHit.of(ISSUE_DATE_MISSING));
		}
		final var subtype = ServingPermits.subtype(permit, periods);
		if (subtype == null) {
			hits.add(RuleHit.of(SUBTYPE_MISSING));
		}
		final var files = permitFiles(permit, place, issuingErrands, hits);

		final var placeName = place == null ? null : place.namn();
		final var restaurantNumber = place == null ? null : place.restaurangBeteckning();
		final var name = Optional.ofNullable(placeName).orElse(UNKNOWN_SERVING_PLACE);
		final var premises = place == null ? null : place(place);
		final var settings = properties.errandType(ErrandType.SERVING_PERMIT);
		final var title = Texts.title("Serveringstillstånd", name);
		final var errands = issuingErrands.stream().map(snapshot.errands()::get).filter(Objects::nonNull).toList();
		final var builder = Candidate.builder(new ItemKey(ALKT, TILLSTAND, String.valueOf(permit.objektId())), ErrandType.SERVING_PERMIT)
			.placeName(placeName)
			.placeNumber(restaurantNumber)
			.ruleHits(hits)
			.holder(holder)
			.place(premises)
			.persons(owner == null ? List.of() : PartyRows.persons(snapshot.personsOf(owner.id()), code -> snapshot.codeText(CODE_TYPE_POSITION, code), today))
			.title(title)
			.investigation(PermitInvestigations.of(title, errands.stream().map(ErrandRow::oppnad).toList(), errands.stream().map(ErrandRow::avslutad).toList(),
				permit.utfardandeDatum(), investigationTexts(issuingErrands, permit.notes())))
			.description(permitDescription(name, content))
			.labelPaths(settings.labels())
			.externalTag(TAG_DIARY_NUMBER, permit.utfardandeDiarienr())
			.parameters(new ParameterList()
				.add("alktObjektId", "Id i AlkT", String.valueOf(permit.objektId()))
				.add(ErrandParameters.ISSUED, "Utfärdat", SourceValues.format(permit.utfardandeDatum()))
				.add(ErrandParameters.DIARY_NUMBER, "Diarienummer i AlkT", permit.utfardandeDiarienr())
				.add(ErrandParameters.HANDLERS, "Handläggare i AlkT", snapshot.visits().handlerNames(errands.stream().flatMap(errand -> errand.handlaggare().stream()).toList()))
				.add("ersatter", "Ersätter", Texts.join(", ", Arrays.asList(SourceValues.format(permit.ersatterDatum()), permit.ersatterDiarienr())))
				.add("drycker", "Drycker", content.drinks())
				.add("serveringTill", "Servering till", content.servingTo())
				.add("omfattning", "Omfattning", content.scope())
				.add("serveringstider", "Serveringstider", content.servingTimes())
				.add("aretRunt", "Året runt", permit.aretRunt() ? "Ja" : "Nej")
				.add("sasong", "Säsong", periods.seasonText())
				.add("period", "Period", periods.periodText())
				.add("hogstaAntal", "Högsta antal gäster", permit.hogstaAntal())
				.add("sittplatser", "Sittplatser", permit.sittplatser())
				.add("serveringslokal", "Serveringslokal", permit.serveringslokal())
				.add("villkor", "Villkor", content.conditions())
				.add(ErrandParameters.NOTES, "Noteringar i AlkT", permit.notes())
				.add(ErrandParameters.LATEST_DECISION, "Senaste beslut i AlkT", decisionText(decision, true))
				.add(ErrandParameters.EVENT_LOG, "Händelselogg i AlkT", Journal.lines(entries(snapshot.placeTextsOf(permit.objektId()), null)))
				.add(CandidateTexts.migrated(today, ALKT))
				.build())
			.asset(new AssetData(null, "Serveringstillstånd för " + name, permit.utfardandeDatum(),
				validTo(permit, periods),
				assetParameters(content, holder, premises, restaurantNumber), legalBasisDocuments(permit, decision, files)))
			.files(files.build(), files.assetFiles(), files.details());
		Optional.ofNullable(subtype).flatMap(settings::subtypeLabel).ifPresent(builder::labelPath);
		return builder.build();
	}

	private Candidate inspectionErrand(final ErrandRow errand, final InspectionCandidates inspections) {
		final var place = placeOf(errand.objektId());
		final var source = new InspectionErrand(ALKT, errand.arendeId(), errand.arendeTyp(), snapshot.codeText(CODE_TYPE_ERRAND, errand.arendeTyp()), errand.diarieNr(), errand.oppnad(),
			errand.avslutad(), snapshot.visits().handlerNames(errand.handlaggare()), errand.upplagd(), latestActivity(errand.arendeId()));
		final var applicant = errand.sokandeId() == null ? null : snapshot.applicants().get(errand.sokandeId());
		final var decisions = decisionsByErrand.getOrDefault(errand.arendeId(), List.of()).stream()
			.sorted((a, b) -> Objects.compare(a.beslutsDatum(), b.beslutsDatum(), Comparator.nullsFirst(Comparator.naturalOrder())))
			.map(decision -> decisionText(decision, false))
			.filter(Objects::nonNull)
			.toList();

		final var files = errandFiles(List.of(errand.arendeId()), new FileList(sharedFileNames));
		return inspections.builder(source, place == null ? null : place(place), applicant, ownerOf(place).orElse(null), placePermit(errand.objektId()), decisions,
			investigationTexts(List.of(errand.arendeId())))
			.placeNumber(place == null ? null : place.restaurangBeteckning())
			.files(files.build(), files.assetFiles(), files.details())
			.build();
	}

	/**
	 * @return the time of the errand's latest event, picture or decision, null when it has none with a time
	 */
	private LocalDateTime latestActivity(final Integer arendeId) {
		return Stream.concat(snapshot.textsOf(arendeId).stream().map(TextRow::tidpunkt), decisionsOf(arendeId).stream().map(DecisionRow::beslutad))
			.filter(Objects::nonNull)
			.max(Comparator.naturalOrder())
			.orElse(null);
	}

	/**
	 * @return the current permit of the place, which its inspections are related to (decision 61); null when the place
	 *         has none. A place with several permits has no day that tells which holder an inspection concerned.
	 */
	private PlacePermit placePermit(final Integer objektId) {
		final var rows = permitRowsByPlace.getOrDefault(objektId, List.of());
		if (rows.isEmpty()) {
			return null;
		}
		final var key = new ItemKey(ALKT, TILLSTAND, String.valueOf(objektId));
		if (rows.size() > 1) {
			return new PlacePermit(key, null, null, false);
		}
		final var permit = rows.getFirst();
		return new PlacePermit(key, permit.utfardandeDatum(), validTo(permit, PermitPeriods.of(permit)), issuedToOwner(permit, placeOf(objektId)));
	}

	/**
	 * @return false when the permit may have been issued to another than the current owner of the place (G2), or the place
	 *         is missing; true when either owner is unknown. Two owner posts with the same number are the same holder, as
	 *         holders are compared everywhere (section 7.7.1), and without a number for both the posts are compared.
	 */
	private boolean issuedToOwner(final PermitRow permit, final PlaceRow place) {
		if (place == null) {
			return false;
		}
		if (permit.gtAgarId() == null || place.agarId() == null || permit.gtAgarId().equals(place.agarId())) {
			return true;
		}
		final var permitNumber = Optional.ofNullable(snapshot.owners().get(permit.gtAgarId())).flatMap(owner -> PartyRows.numberKey(owner.organisationsNr(), today));
		final var placeNumber = Optional.ofNullable(snapshot.owners().get(place.agarId())).flatMap(owner -> PartyRows.numberKey(owner.organisationsNr(), today));
		return permitNumber.isPresent() && permitNumber.equals(placeNumber);
	}

	private static String ownerText(final PlaceRow place, final ContactRow owner) {
		if (owner != null) {
			return "AgarID " + owner.id();
		}
		return place.agarId() == null ? "serveringsstället saknar ägare" : "AgarID %d finns inte".formatted(place.agarId());
	}

	/**
	 * @return the last day of a permit that holds for a period of dates and not all year, null for any other
	 */
	private static LocalDate validTo(final PermitRow permit, final PermitPeriods periods) {
		return !permit.aretRunt() && periods.hasDatePeriod() ? periods.periodEnd() : null;
	}

	private Candidate visit(final VisitRow visit, final InspectionVisitCandidates visits) {
		final var place = placeOf(visit.objektId());
		final var files = new FileList(sharedFileNames);
		snapshot.visits().forEachFile(visit, (name, folder) -> files.add(name, folder, FilePurposes.ofVisit(ALKT, folder, name, SourceValues.startOfDay(visit.datum()))));
		return visits.builder(visit, snapshot.visits(), place == null ? null : place(place), placePermit(visit.objektId()))
			.placeNumber(place == null ? null : place.restaurangBeteckning())
			.files(files.build(), files.assetFiles(), files.details())
			.build();
	}

	/**
	 * The rules about the place and its owner: G26, G1, G2, G6, G24, G8 and G7.
	 */
	private void placeAndOwnerHits(final PermitRow permit, final PlaceRow place, final ContactRow owner, final Holder holder, final List<RuleHit> hits) {
		final var rowsOnPlace = permitRowsByPlace.getOrDefault(permit.objektId(), List.of()).size();
		if (rowsOnPlace > 1) {
			hits.add(RuleHit.of(PERMIT_ROWS_ON_PLACE, "%d rader".formatted(rowsOnPlace)));
		}
		if (place == null) {
			hits.add(RuleHit.of(PLACE_MISSING));
			return;
		}
		if (!issuedToOwner(permit, place)) {
			hits.add(RuleHit.of(OWNER_DIFFERS, "AgarID i tillståndet %d, på serveringsstället %d".formatted(permit.gtAgarId(), place.agarId())));
		}
		final var restaurantNumber = place.restaurangBeteckning();
		if (restaurantNumber == null || !restaurantNumber.matches("\\d{8}")) {
			hits.add(RuleHit.of(RESTAURANT_NUMBER_INVALID, restaurantNumber == null ? null : "'%s'".formatted(restaurantNumber)));
		}
		if (permit.gtRestaurangBeteckning() != null && restaurantNumber != null && !digitsOf(permit.gtRestaurangBeteckning()).equals(digitsOf(restaurantNumber))) {
			hits.add(RuleHit.of(RESTAURANT_NUMBER_DIFFERS, "på tillståndet %s, på serveringsstället %s".formatted(permit.gtRestaurangBeteckning(), restaurantNumber)));
		}
		if (holder == null || holder.legalId() == null) {
			hits.add(RuleHit.of(LEGAL_ID_INVALID, ownerText(place, owner)));
		}
		Optional.ofNullable(owner)
			.flatMap(found -> PartyRows.numberKey(found.organisationsNr(), today))
			.filter(number -> ownersByNumber.getOrDefault(number, Set.of()).size() > 1)
			.ifPresent(_ -> hits.add(RuleHit.of(LEGAL_ID_ON_SEVERAL_OWNERS, "AgarID " + owner.id())));
	}

	/**
	 * @return the decision the permit points to; a decision that has expired hits G3, and one that is missing G27
	 */
	private DecisionRow decisionOf(final PermitRow permit, final List<RuleHit> hits) {
		if (permit.beslutsId() == null) {
			return null;
		}
		final var decision = snapshot.decisions().get(permit.beslutsId());
		if (decision == null) {
			hits.add(RuleHit.of(DECISION_MISSING, "BeslutsID " + permit.beslutsId()));
		} else if (decision.giltigtTom() != null && decision.giltigtTom().isBefore(today)) {
			hits.add(RuleHit.of(DECISION_EXPIRED, "beslutstyp %s, giltigt till %s".formatted(decision.beslutsTyp(), decision.giltigtTom())));
		}
		return decision;
	}

	/**
	 * The rules about the season and the period: G9, G4, G5 and G12.
	 */
	private void periodHits(final PermitRow permit, final PermitPeriods periods, final List<RuleHit> hits) {
		periods.problems().forEach(problem -> hits.add(RuleHit.of(UNPARSEABLE, problem)));
		if (periods.periodEnd() != null && periods.periodEnd().isBefore(today)) {
			hits.add(RuleHit.of(PERIOD_PASSED, periods.periodText()));
		}
		if (!permit.aretRunt() && Texts.isBlank(Texts.joinWords(permit.arligPeriodStart(), permit.arligPeriodSlut(), permit.periodStart(), permit.periodSlut()))) {
			hits.add(RuleHit.of(PERIOD_MISSING));
		}
		if (permit.aretRunt() && periods.hasDatePeriod()) {
			hits.add(RuleHit.of(PERIOD_ON_ALL_YEAR, periods.periodText()));
		}
	}

	/**
	 * Ä1 for every errand that issued the permit and is not closed in AlkT (decision 16).
	 */
	private void openErrandHits(final Set<Integer> issuingErrands, final List<RuleHit> hits) {
		issuingErrands.stream()
			.map(snapshot.errands()::get)
			.filter(Objects::nonNull)
			.filter(errand -> errand.avslutad() == null)
			.forEach(errand -> hits.add(RuleHit.of(UNDERLYING_ERRAND_OPEN, CandidateTexts.errand(errand.arendeId(), errand.diarieNr()))));
	}

	/**
	 * The rules about the diary number: G15 and G16.
	 */
	private void diaryNumberHits(final PermitRow permit, final List<RuleHit> hits) {
		final var diaryNumber = DiaryNumbers.normalize(permit.utfardandeDiarienr());
		if (diaryNumber == null) {
			hits.add(RuleHit.of(DIARY_NUMBER_MISSING));
		} else if (permitsByDiaryNumber.getOrDefault(diaryNumber, 0) > 1) {
			hits.add(RuleHit.of(DIARY_NUMBER_ON_SEVERAL, permit.utfardandeDiarienr()));
		}
	}

	private PlaceRow placeOf(final Integer objektId) {
		return objektId == null ? null : snapshot.places().get(objektId);
	}

	/**
	 * A restaurant number without the spaces and dots it is sometimes written with on the permit.
	 */
	private static String digitsOf(final String restaurantNumber) {
		return restaurantNumber.replaceAll("[\\h\\v.]", "");
	}

	private Optional<ContactRow> ownerOf(final PlaceRow place) {
		return Optional.ofNullable(place).map(PlaceRow::agarId).map(snapshot.owners()::get);
	}

	private Place place(final PlaceRow place) {
		final var group = place.objektGrupp1();
		final var marking = group != null && PLACE_MARKINGS.contains(group.toUpperCase(Locale.ROOT));
		final var area = snapshot.codeText(CODE_TYPE_AREA, place.omrade());
		return new Place(place.namn(), place.adress(), place.postnummer(), place.postort(), place.telefon(), place.email(), area, new ParameterList()
			.add(ERRAND_PARAMETER_RESTAURANT_NUMBER, "Valt restaurangnummer", place.restaurangBeteckning())
			.add("typAvServeringsstalle", "Typ av serveringsställe", marking ? null : snapshot.codeText(CODE_TYPE_PLACE_GROUP, group))
			.add("markering", "Markering", marking ? snapshot.codeText(CODE_TYPE_PLACE_GROUP, group) : null)
			.add(ErrandParameters.AREA, "Område", area)
			.build());
	}

	private List<String> servingTimes(final PermitRow permit, final List<RuleHit> hits) {
		final var times = new ArrayList<String>();
		for (var i = 0; i < permit.servingTimes().size(); i++) {
			final var time = permit.servingTimes().get(i);
			if (time.start() == null && time.end() == null) {
				continue;
			}
			final var start = SourceValues.parseTime(time.start());
			final var end = SourceValues.parseTime(time.end());
			if (start.isPresent() && end.isPresent()) {
				times.add(Texts.range(start.get(), end.get()));
			} else {
				final var raw = Texts.range(time.start(), time.end());
				hits.add(RuleHit.of(UNPARSEABLE, "serveringstid %d '%s'".formatted(i + 1, raw)));
				times.add(raw);
			}
		}
		return times;
	}

	/**
	 * The conditions of the place, without those whose last day has passed (section 7.3.3).
	 */
	private List<String> conditions(final Integer objektId, final List<RuleHit> hits) {
		final var texts = new ArrayList<String>();
		for (final ConditionRow condition : snapshot.conditionsOf(objektId)) {
			final var validTo = SourceValues.parseDate(condition.validTo());
			if (condition.validTo() != null && validTo.isEmpty()) {
				hits.add(RuleHit.of(UNPARSEABLE, "villkorets slutdatum '%s'".formatted(condition.validTo())));
			}
			if (condition.validFrom() != null && SourceValues.parseDate(condition.validFrom()).isEmpty()) {
				hits.add(RuleHit.of(UNPARSEABLE, "villkorets startdatum '%s'".formatted(condition.validFrom())));
			}
			if (validTo.isPresent() && validTo.get().isBefore(today)) {
				continue;
			}
			Optional.ofNullable(conditionText(condition)).ifPresent(texts::add);
		}
		return texts;
	}

	/**
	 * @return the text of the condition's code and its own text, as "code text: text" when it has both, or null when it
	 *         has neither
	 */
	private String conditionText(final ConditionRow condition) {
		final var codeText = condition.code() == null ? null : snapshot.codeText(CODE_TYPE_CONDITION, condition.code());
		if (codeText == null) {
			return condition.text();
		}
		return condition.text() == null ? codeText : codeText + ": " + condition.text();
	}

	/**
	 * The errands that issued the permit: the errand of the decision the permit points to, and the errands of the place
	 * with the permit's diary number (section 3.2).
	 */
	private Set<Integer> issuingErrands(final PermitRow permit) {
		final var errands = new LinkedHashSet<Integer>();
		Optional.ofNullable(permit.beslutsId())
			.map(snapshot.decisions()::get)
			.map(DecisionRow::arendeId)
			.ifPresent(errands::add);
		final var diaryNumber = DiaryNumbers.normalize(permit.utfardandeDiarienr());
		if (diaryNumber != null) {
			snapshot.errands().values().stream()
				.filter(errand -> permit.objektId().equals(errand.objektId()))
				.filter(errand -> diaryNumber.equals(DiaryNumbers.normalize(errand.diarieNr())))
				.map(ErrandRow::arendeId)
				.forEach(errands::add);
		}
		return errands;
	}

	/**
	 * The files of the permit: those of the errands that issued it and of the place (decision 7), and those of the place's
	 * events without an errand (decision 37). The latest certificate and annexes of the place, and the drawings and the
	 * permit decisions of the issuing errands, also go on the post in PartyAssets, in that order of precedence (decisions
	 * 34, 35 and 39). A certificate older than the permit, or without a date, may be a former holder's: it goes on the
	 * errand only and hits G29, as does a permit without a certificate. An annex older than the permit, or of catering on a
	 * permit without catering, is left out.
	 */
	private FileList permitFiles(final PermitRow permit, final PlaceRow place, final Set<Integer> issuingErrands, final List<RuleHit> hits) {
		final var files = errandFiles(issuingErrands, new FileList(sharedFileNames));
		if (place != null) {
			Stream.of(place.bifogadfil(), place.bifogadfil2()).forEach(name -> files.add(name, ATTACHMENTS, FilePurposes.detailsOf(ALKT, ATTACHMENTS, name, null)));
		}
		snapshot.placeFilesOf(permit.objektId()).forEach(file -> files.add(file.name(), file.generated() ? DOCUMENTS : ATTACHMENTS, file.details()));

		latestDocument(permit.objektId(), CERTIFICATE_TEMPLATES).ifPresentOrElse(certificate -> {
			files.add(certificate.name(), DOCUMENTS, certificate.details());
			if (isCurrent(certificate, permit)) {
				files.add(certificate.name(), DOCUMENTS, ASSET_CATEGORY_CERTIFICATE);
			} else {
				files.addToErrandOnly(certificate.name(), DOCUMENTS);
				hits.add(RuleHit.of(CERTIFICATE_OLDER, certificate.datum() == null ? "beviset saknar datum"
					: "beviset är från %s, tillståndet utfärdades %s".formatted(certificate.datum(), permit.utfardandeDatum())));
			}
		}, () -> hits.add(RuleHit.of(CERTIFICATE_MISSING)));
		latestDocument(permit.objektId(), ANNEX_TEMPLATES)
			.filter(annex -> isCurrent(annex, permit))
			.ifPresent(annex -> files.add(annex.name(), DOCUMENTS, annex.details()).add(annex.name(), DOCUMENTS, ASSET_CATEGORY_ANNEX));
		latestDocument(permit.objektId(), CATERING_ANNEX_TEMPLATES)
			.filter(annex -> permit.catering() && isCurrent(annex, permit))
			.ifPresent(annex -> files.add(annex.name(), DOCUMENTS, annex.details()).add(annex.name(), DOCUMENTS, ASSET_CATEGORY_ANNEX));

		for (final var arendeId : issuingErrands) {
			snapshot.filesOf(arendeId).stream()
				// The purpose rather than the name, since a referral answer named as a drawing stays on the errand
				.filter(file -> !file.generated() && FilePurposes.isDrawing(file.details().purpose()))
				.forEach(file -> files.add(file.name(), ATTACHMENTS, ASSET_CATEGORY_DRAWING));
			decisionsOf(arendeId).stream()
				.filter(decision -> PermitFiles.isDrawing(decision.bifogadFil()))
				.forEach(decision -> files.add(decision.bifogadFil(), ATTACHMENTS, ASSET_CATEGORY_DRAWING));
		}
		if (place != null) {
			Stream.of(place.bifogadfil(), place.bifogadfil2())
				.filter(PermitFiles::isDrawing)
				.forEach(name -> files.add(name, ATTACHMENTS, ASSET_CATEGORY_DRAWING));
		}
		issuingErrands.forEach(arendeId -> decisionsOf(arendeId).forEach(decision -> PermitFiles.markPermitDecision(files, decision.mall(), decision.worddokument(), decision.bifogadFil(), DECISION_TEMPLATES)));
		return files;
	}

	/**
	 * @return the documents that state the legal basis of the permit, which AlkT keeps nowhere else (decision 58): the
	 *         document of the decision the permit points to, when it is made from the template of a permit decision, and
	 *         then the latest certificate; none that the permit keeps on its errand only, such as an older certificate
	 */
	private List<FileReference> legalBasisDocuments(final PermitRow permit, final DecisionRow decision, final FileList files) {
		final var decisionDocument = Optional.ofNullable(decision)
			.filter(row -> PermitFiles.isTemplate(row.mall(), DECISION_TEMPLATES))
			.map(DecisionRow::worddokument);
		final var certificate = latestDocument(permit.objektId(), CERTIFICATE_TEMPLATES).map(PlaceDocument::name);
		return Stream.of(decisionDocument, certificate)
			.flatMap(Optional::stream)
			.map(name -> files.findOfHolder(name, DOCUMENTS))
			.flatMap(Optional::stream)
			.toList();
	}

	/**
	 * @return true when the document is from the day the permit was issued or later; a permit without an issue date is
	 *         held for review by G9 anyway
	 */
	private static boolean isCurrent(final PlaceDocument document, final PermitRow permit) {
		return document.datum() != null && (permit.utfardandeDatum() == null || !document.datum().isBefore(permit.utfardandeDatum()));
	}

	/**
	 * @return the latest document of the place made from one of the templates, from its events or decisions
	 */
	private Optional<PlaceDocument> latestDocument(final Integer objektId, final Set<String> templates) {
		return documentsByPlace.getOrDefault(objektId, List.of()).stream()
			.filter(document -> PermitFiles.isTemplate(document.mall(), templates))
			.max(DOCUMENT_ORDER);
	}

	private List<DecisionRow> decisionsOf(final Integer arendeId) {
		return decisionsByErrand.getOrDefault(arendeId, List.of());
	}

	/**
	 * Indexes the documents of the events and the decisions by their place: the place of their errand, or, for an event
	 * without an errand, the event's own place.
	 */
	private void indexDocuments() {
		snapshot.documents().forEach(document -> Optional.ofNullable(document.arendeId())
			.map(snapshot.errands()::get)
			.map(ErrandRow::objektId)
			.or(() -> Optional.ofNullable(document.objektId()))
			.ifPresent(objektId -> documentsByPlace.computeIfAbsent(objektId, _ -> new ArrayList<>())
				.add(new PlaceDocument(document.name(), document.mall(), document.tidpunkt(), document.handelseId()))));
		snapshot.decisions().values().stream()
			.filter(decision -> decision.worddokument() != null && decision.arendeId() != null)
			.forEach(decision -> Optional.ofNullable(snapshot.errands().get(decision.arendeId()))
				.map(ErrandRow::objektId)
				.ifPresent(objektId -> documentsByPlace.computeIfAbsent(objektId, _ -> new ArrayList<>())
					.add(new PlaceDocument(decision.worddokument(), decision.mall(), decision.beslutad(), decision.beslutsId()))));
	}

	private FileList errandFiles(final Collection<Integer> arendeIds, final FileList files) {
		for (final var arendeId : arendeIds) {
			snapshot.filesOf(arendeId).forEach(file -> files.add(file.name(), file.generated() ? DOCUMENTS : ATTACHMENTS, file.details()));
			decisionsByErrand.getOrDefault(arendeId, List.of()).forEach(decision -> {
				final var received = decision.beslutad();
				files.add(decision.worddokument(), DOCUMENTS, FilePurposes.ofGenerated(ALKT, decision.mall(), null, received))
					.add(decision.bifogadFil(), ATTACHMENTS, FilePurposes.detailsOf(ALKT, ATTACHMENTS, decision.bifogadFil(), received));
			});
		}
		return files;
	}

	/**
	 * Every file reference of AlkT with the place it belongs to: the place of its errand or visit, or the errand or visit
	 * itself when it has no place, and the place's own files.
	 */
	private static SharedFileNames sharedFileNames(final AlktSnapshot snapshot, final AotLoaderProperties properties) {
		final var names = SharedFileNames.builder(properties.fileArea(ALKT));
		snapshot.errandFiles().forEach((arendeId, files) -> {
			final var place = placeKeyOfErrand(snapshot, arendeId);
			files.forEach(file -> names.add(place, file.name(), file.generated() ? DOCUMENTS : ATTACHMENTS));
		});
		snapshot.decisions().values().forEach(decision -> {
			final var place = decision.arendeId() == null ? "beslut:" + decision.beslutsId() : placeKeyOfErrand(snapshot, decision.arendeId());
			names.add(place, decision.worddokument(), DOCUMENTS).add(place, decision.bifogadFil(), ATTACHMENTS);
		});
		snapshot.places().forEach((objektId, place) -> names
			.add(placeKey(objektId), place.bifogadfil(), ATTACHMENTS)
			.add(placeKey(objektId), place.bifogadfil2(), ATTACHMENTS));
		snapshot.placeFiles().forEach((objektId, files) -> files
			.forEach(file -> names.add(placeKey(objektId), file.name(), file.generated() ? DOCUMENTS : ATTACHMENTS)));
		snapshot.unplacedFiles().forEach((handelseId, files) -> files
			.forEach(file -> names.add("handelse:" + handelseId, file.name(), file.generated() ? DOCUMENTS : ATTACHMENTS)));
		snapshot.visits().visits().forEach(visit -> {
			final var place = visit.objektId() == null ? "besok:" + visit.tillfalleNr() : placeKey(visit.objektId());
			snapshot.visits().forEachFile(visit, (name, folder) -> names.add(place, name, folder));
		});
		return names.build();
	}

	private static String placeKeyOfErrand(final AlktSnapshot snapshot, final Integer arendeId) {
		return Optional.ofNullable(snapshot.errands().get(arendeId))
			.map(ErrandRow::objektId)
			.map(AlktCandidateFactory::placeKey)
			.orElse("arende:" + arendeId);
	}

	private static String placeKey(final Integer objektId) {
		return "objekt:" + objektId;
	}

	/**
	 * The parameters of the permit, under the keys of the AoT solution (section 7.4).
	 */
	private static Map<String, String> assetParameters(final PermitContent content, final Holder holder, final Place premises, final String restaurantNumber) {
		final var permit = content.permit();
		final var periods = content.periods();
		return new AssetParameters()
			.holder(holder)
			.premises(premises, restaurantNumber)
			.flag(AssetParameters.SERVING_TO_PUBLIC, permit.servingPublic())
			.flag(AssetParameters.SERVING_TO_CLOSED_COMPANY, permit.servingPrivate())
			.flag(AssetParameters.SCOPE_ALL_YEAR, permit.aretRunt())
			.flag(AssetParameters.SCOPE_ANNUAL_PERIOD, !permit.aretRunt() && periods.seasonText() != null)
			.flag(AssetParameters.SCOPE_TASTING, permit.provsmakning())
			.lines(AssetParameters.SCOPE_PERIOD_FROM_TO, Arrays.asList(periods.seasonText(), periods.periodText()))
			.flag(AssetParameters.BEVERAGE_SPIRITS, permit.spritdrycker())
			.flag(AssetParameters.BEVERAGE_WINE, permit.vin())
			.flag(AssetParameters.BEVERAGE_BEER, permit.starkol())
			.flag(AssetParameters.BEVERAGE_OTHER_FERMENTED, permit.ajaDrycker())
			.text(AssetParameters.SERVING_AREA_DESCRIPTION, permit.serveringslokal())
			.text(AssetParameters.SERVING_AREA_SEATS, permit.sittplatser())
			.text(AssetParameters.SERVING_AREA_MAX_PERSONS, permit.hogstaAntal())
			.lines(AssetParameters.SERVING_HOURS, content.servingTimes())
			.lines(ASSET_PARAMETER_CONDITIONS, content.conditions())
			.text(AssetParameters.CASE_NUMBER, permit.utfardandeDiarienr())
			.text(AssetParameters.DECISION_DATE_AND_CASE_NUMBER, Texts.join(", ", Arrays.asList(SourceValues.format(permit.utfardandeDatum()), permit.utfardandeDiarienr())))
			.text(AssetParameters.DIARY_NUMBER, permit.utfardandeDiarienr())
			.text(AssetParameters.MIGRATED_FROM, ALKT.getDisplayName())
			.build();
	}

	private static String permitDescription(final String name, final PermitContent content) {
		final var permit = content.permit();
		final var lines = new ArrayList<String>();
		lines.add("Serveringstillstånd för " + name + ", migrerat från " + ALKT.getDisplayName() + ".");
		lines.add(CandidateTexts.issuedLine(permit.utfardandeDatum(), permit.utfardandeDiarienr()));
		lines.add(line("Drycker", Texts.join(", ", content.drinks())));
		lines.add(line("Servering till", Texts.join(", ", content.servingTo())));
		lines.add(line("Omfattning", Texts.join(", ", content.scope())));
		lines.add(line("Serveringstider", Texts.join(", ", content.servingTimes())));
		lines.add(line("Året runt", permit.aretRunt() ? "Ja" : "Nej"));
		lines.add(line("Säsong", content.periods().seasonText()));
		lines.add(line("Period", content.periods().periodText()));
		if (!content.conditions().isEmpty()) {
			lines.add("Villkor:");
			content.conditions().forEach(condition -> lines.add("- " + condition));
		}
		return Texts.join("\n", lines);
	}

	/**
	 * @return the texts of the investigation of the errands: their notes, their events and the texts of their pictures, and
	 *         their decisions with their texts and who made them (decisions 44, 46, 52 and 53)
	 */
	private InvestigationTexts investigationTexts(final Collection<Integer> arendeIds) {
		return investigationTexts(arendeIds, List.of());
	}

	/**
	 * @param  shownNotes notes the errand already shows as a parameter, which the investigation leaves out, since AlkT
	 *                    copies the notes of the issuing errand to the permit
	 * @return            the texts of the investigation of the errands, as above, without the notes already shown
	 */
	private InvestigationTexts investigationTexts(final Collection<Integer> arendeIds, final Collection<String> shownNotes) {
		return Journal.investigation(
			arendeIds.stream().map(snapshot.errands()::get).filter(Objects::nonNull).flatMap(errand -> errand.notes().stream()).filter(note -> !shownNotes.contains(note)).distinct().toList(),
			arendeIds.stream()
				.flatMap(arendeId -> entries(snapshot.textsOf(arendeId), Optional.ofNullable(snapshot.errands().get(arendeId)).map(ErrandRow::diarieNr).orElse(null)).stream())
				.toList(),
			arendeIds.stream().flatMap(arendeId -> decisionsOf(arendeId).stream())
				.map(decision -> new Journal.Entry(decision.beslutad(),
					Journal.decisionHeading(snapshot.codeText(CODE_TYPE_DECISION, decision.beslutsTyp()), snapshot.visits().handlerName(decision.beslutsfattare())),
					decision.beslutsText()))
				.toList());
	}

	/**
	 * @param  errandDiaryNumber the diary number of the errand of the events, null for events without an errand
	 * @return                   the events and pictures as entries of a journal, each headed by the text of its event type,
	 *                           marked when confidential and with its own diary number when it is another than the
	 *                           errand's, or "Bild" for a picture
	 */
	private List<Journal.Entry> entries(final List<TextRow> texts, final String errandDiaryNumber) {
		return texts.stream()
			.map(text -> new Journal.Entry(text.tidpunkt(), text.picture() ? PICTURE_HEADING
				: Journal.eventHeading(snapshot.codeText(CODE_TYPE_EVENT, text.handelseTyp()), text.confidential(), Journal.otherDiaryNumber(text.diarieNr(), errandDiaryNumber)),
				text.text()))
			.toList();
	}

	private String decisionText(final DecisionRow decision, final boolean withValidTo) {
		if (decision == null) {
			return null;
		}
		return Texts.join(", ", Arrays.asList(
			snapshot.codeText(CODE_TYPE_DECISION, decision.beslutsTyp()),
			SourceValues.format(decision.beslutsDatum()),
			withValidTo && decision.giltigtTom() != null ? "giltigt till " + decision.giltigtTom() : null));
	}

	/**
	 * A document of a place, from an event or a decision.
	 *
	 * @param tidpunkt when the event or decision was, null when unknown
	 * @param order    the id of the event or decision, which orders documents of the same time
	 */
	private record PlaceDocument(String name, String mall, LocalDateTime tidpunkt, Integer order) {

		LocalDate datum() {
			return tidpunkt == null ? null : tidpunkt.toLocalDate();
		}

		FileDetails details() {
			return FilePurposes.ofGenerated(ALKT, mall, null, tidpunkt);
		}
	}

	/**
	 * What a serving permit holds, interpreted once for the errand, its description and the permit in PartyAssets.
	 *
	 * @param permit       the row of the permit
	 * @param periods      its season and date period
	 * @param servingTimes its serving times, "hh:mm–hh:mm", or as the source has them when they cannot be read
	 * @param conditions   its conditions that still apply
	 */
	record PermitContent(PermitRow permit, PermitPeriods periods, List<String> servingTimes, List<String> conditions) {

		List<String> drinks() {
			return ServingPermits.drinks(permit);
		}

		List<String> servingTo() {
			return ServingPermits.servingTo(permit);
		}

		List<String> scope() {
			return ServingPermits.scope(permit);
		}

		@Override
		public String toString() {
			return "PermitContent[permit=" + permit + "]";
		}
	}
}
