package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
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
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReaderResult.Notice;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.AssetParameters;
import se.sundsvall.aotloader.source.CandidateTexts;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.FileList;
import se.sundsvall.aotloader.source.InspectionCandidates;
import se.sundsvall.aotloader.source.InspectionCandidates.InspectionErrand;
import se.sundsvall.aotloader.source.InspectionVisitCandidates;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.source.ParameterList;
import se.sundsvall.aotloader.source.PartyRows;
import se.sundsvall.aotloader.source.PermitFiles;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ConditionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PermitRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PlaceRow;
import se.sundsvall.aotloader.source.files.SharedFileNames;
import se.sundsvall.aotloader.support.DiaryNumbers;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_ANNEX;
import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_CERTIFICATE;
import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_DECISION;
import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_DRAWING;
import static se.sundsvall.aotloader.Constants.ASSET_PARAMETER_CONDITIONS;
import static se.sundsvall.aotloader.Constants.PARAMETER_RESTAURANT_NUMBER;
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
	private static final Comparator<PlaceDocument> DOCUMENT_ORDER = Comparator
		.comparing(PlaceDocument::datum, Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
		.thenComparing(PlaceDocument::order, Comparator.nullsFirst(Comparator.<Integer>naturalOrder()));

	private final AlktSnapshot snapshot;
	private final LocalDate today;
	private final AotLoaderProperties properties;
	private final Map<String, Set<Integer>> ownersByNumber = new HashMap<>();
	private final Map<String, Integer> permitsByDiaryNumber = new HashMap<>();
	private final Map<Integer, Integer> permitRowsByPlace = new HashMap<>();
	private final Map<Integer, List<DecisionRow>> decisionsByErrand = new HashMap<>();
	private final Map<Integer, List<PlaceDocument>> documentsByPlace = new HashMap<>();
	private final SharedFileNames sharedFileNames;

	public AlktCandidateFactory(final AlktSnapshot snapshot, final LocalDate today, final AotLoaderProperties properties) {
		this.snapshot = snapshot;
		this.today = today;
		this.properties = properties;
		this.sharedFileNames = sharedFileNames(snapshot, properties);
		snapshot.permits().forEach(permit -> {
			Optional.ofNullable(permit.objektId()).ifPresent(objektId -> permitRowsByPlace.merge(objektId, 1, Integer::sum));
			Optional.ofNullable(DiaryNumbers.normalize(permit.utfardandeDiarienr())).ifPresent(number -> permitsByDiaryNumber.merge(number, 1, Integer::sum));
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
	 * @return the current serving permits, with a notice for each permit that has no ObjektID and therefore no id
	 */
	public ReaderResult permits() {
		final var candidates = new ArrayList<Candidate>();
		final var notices = new ArrayList<Notice>();
		var withoutId = 0;
		for (final var permit : snapshot.permits()) {
			if (permit.objektId() == null) {
				withoutId++;
				notices.add(new Notice(new ItemKey(ALKT, TILLSTAND, WITHOUT_ID + withoutId), null, Outcome.GRANSKAS,
					PLACE_MISSING.getCode() + " Tillståndet saknar ObjektID, diarienummer " + Optional.ofNullable(permit.utfardandeDiarienr()).orElse("saknas")));
				continue;
			}
			candidates.add(permit(permit));
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
		final var builder = Candidate.builder(new ItemKey(ALKT, TILLSTAND, String.valueOf(permit.objektId())), ErrandType.SERVING_PERMIT)
			.placeName(placeName)
			.placeNumber(restaurantNumber)
			.ruleHits(hits)
			.holder(holder)
			.place(premises)
			.persons(owner == null ? List.of() : PartyRows.persons(snapshot.personsOf(owner.id()), code -> snapshot.codeText(CODE_TYPE_POSITION, code), today))
			.title(Texts.title("Serveringstillstånd", name))
			.description(permitDescription(name, content))
			.labelPaths(settings.labels())
			.externalTag(TAG_DIARY_NUMBER, permit.utfardandeDiarienr())
			.parameters(new ParameterList()
				.add("alktObjektId", "AlkT-id", String.valueOf(permit.objektId()))
				.add("utfardat", "Utfärdat", SourceValues.format(permit.utfardandeDatum()))
				.add("diarienummer", "Diarienummer i AlkT", permit.utfardandeDiarienr())
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
				.add("noteringar", "Noteringar i AlkT", permit.notes())
				.add("senasteBeslut", "Senaste beslut i AlkT", decisionText(decision, true))
				.add(CandidateTexts.migrated(today, ALKT))
				.build())
			.asset(new AssetData(null, "Serveringstillstånd för " + name, permit.utfardandeDatum(),
				!permit.aretRunt() && periods.hasDatePeriod() ? periods.periodEnd() : null,
				assetParameters(content, holder, premises, restaurantNumber)))
			.files(files.build())
			.assetFiles(files.assetFiles());
		Optional.ofNullable(subtype).flatMap(settings::subtypeLabel).ifPresent(builder::labelPath);
		return builder.build();
	}

	private Candidate inspectionErrand(final ErrandRow errand, final InspectionCandidates inspections) {
		final var place = placeOf(errand.objektId());
		final var source = new InspectionErrand(ALKT, errand.arendeId(), errand.arendeTyp(), snapshot.codeText(CODE_TYPE_ERRAND, errand.arendeTyp()), errand.diarieNr(), errand.oppnad(),
			errand.avslutad());
		final var applicant = errand.sokandeId() == null ? null : snapshot.applicants().get(errand.sokandeId());
		final var permitKey = permitRowsByPlace.containsKey(errand.objektId()) ? new ItemKey(ALKT, TILLSTAND, String.valueOf(errand.objektId())) : null;
		final var decisions = decisionsByErrand.getOrDefault(errand.arendeId(), List.of()).stream()
			.sorted((a, b) -> Objects.compare(a.beslutsDatum(), b.beslutsDatum(), Comparator.nullsFirst(Comparator.naturalOrder())))
			.map(decision -> decisionText(decision, false))
			.filter(Objects::nonNull)
			.toList();

		return inspections.builder(source, place == null ? null : place(place), applicant, ownerOf(place).orElse(null), permitKey, decisions)
			.placeNumber(place == null ? null : place.restaurangBeteckning())
			.files(errandFiles(List.of(errand.arendeId()), new FileList(sharedFileNames)).build())
			.build();
	}

	private Candidate visit(final VisitRow visit, final InspectionVisitCandidates visits) {
		final var place = placeOf(visit.objektId());
		final var files = new FileList(sharedFileNames);
		snapshot.visits().forEachFile(visit, files::add);
		return visits.builder(visit, snapshot.visits(), place == null ? null : place(place))
			.placeNumber(place == null ? null : place.restaurangBeteckning())
			.files(files.build())
			.build();
	}

	/**
	 * The rules about the place and its owner: G26, G1, G2, G6, G24, G8 and G7.
	 */
	private void placeAndOwnerHits(final PermitRow permit, final PlaceRow place, final ContactRow owner, final Holder holder, final List<RuleHit> hits) {
		final var rowsOnPlace = permitRowsByPlace.getOrDefault(permit.objektId(), 0);
		if (rowsOnPlace > 1) {
			hits.add(RuleHit.of(PERMIT_ROWS_ON_PLACE, "%d rader".formatted(rowsOnPlace)));
		}
		if (place == null) {
			hits.add(RuleHit.of(PLACE_MISSING));
			return;
		}
		if (permit.gtAgarId() != null && place.agarId() != null && !permit.gtAgarId().equals(place.agarId())) {
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
			hits.add(RuleHit.of(LEGAL_ID_INVALID, owner == null ? "serveringsstället saknar ägare" : "AgarID " + owner.id()));
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
		return restaurantNumber.replaceAll("[\\s.]", "");
	}

	private Optional<ContactRow> ownerOf(final PlaceRow place) {
		return Optional.ofNullable(place).map(PlaceRow::agarId).map(snapshot.owners()::get);
	}

	private Place place(final PlaceRow place) {
		final var group = place.objektGrupp1();
		final var marking = group != null && PLACE_MARKINGS.contains(group.toUpperCase(Locale.ROOT));
		final var area = snapshot.codeText(CODE_TYPE_AREA, place.omrade());
		return new Place(place.namn(), place.adress(), place.postnummer(), place.postort(), place.telefon(), place.email(), area, new ParameterList()
			.add(PARAMETER_RESTAURANT_NUMBER, "Serveringsställenummer", place.restaurangBeteckning())
			.add("typAvServeringsstalle", "Typ av serveringsställe", marking ? null : snapshot.codeText(CODE_TYPE_PLACE_GROUP, group))
			.add("markering", "Markering", marking ? snapshot.codeText(CODE_TYPE_PLACE_GROUP, group) : null)
			.add("omrade", "Område", area)
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
			files.add(place.bifogadfil(), ATTACHMENTS).add(place.bifogadfil2(), ATTACHMENTS);
		}
		snapshot.placeFilesOf(permit.objektId()).forEach(file -> files.add(file.name(), file.generated() ? DOCUMENTS : ATTACHMENTS));

		latestDocument(permit.objektId(), CERTIFICATE_TEMPLATES).ifPresentOrElse(certificate -> {
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
			.ifPresent(annex -> files.add(annex.name(), DOCUMENTS, ASSET_CATEGORY_ANNEX));
		latestDocument(permit.objektId(), CATERING_ANNEX_TEMPLATES)
			.filter(annex -> permit.catering() && isCurrent(annex, permit))
			.ifPresent(annex -> files.add(annex.name(), DOCUMENTS, ASSET_CATEGORY_ANNEX));

		for (final var arendeId : issuingErrands) {
			snapshot.filesOf(arendeId).stream()
				.filter(file -> !file.generated() && PermitFiles.isDrawing(file.name()))
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
		issuingErrands.forEach(arendeId -> decisionsOf(arendeId).forEach(decision -> addDecisionFiles(files, decision)));
		return files;
	}

	/**
	 * Marks the files of a decision that are the permit decision for the post: the document made from the template of the
	 * decision, and an attachment whose name tells it is a permit, a decision or a certificate (decision 39). The others,
	 * such as an investigation or a referral answer, stay on the errand only.
	 */
	private static void addDecisionFiles(final FileList files, final DecisionRow decision) {
		if (PermitFiles.isTemplate(decision.mall(), DECISION_TEMPLATES)) {
			files.add(decision.worddokument(), DOCUMENTS, ASSET_CATEGORY_DECISION);
		}
		if (PermitFiles.isDecision(decision.bifogadFil())) {
			files.add(decision.bifogadFil(), ATTACHMENTS, ASSET_CATEGORY_DECISION);
		}
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
				.add(new PlaceDocument(document.name(), document.mall(), document.datum(), document.handelseId()))));
		snapshot.decisions().values().stream()
			.filter(decision -> decision.worddokument() != null && decision.arendeId() != null)
			.forEach(decision -> Optional.ofNullable(snapshot.errands().get(decision.arendeId()))
				.map(ErrandRow::objektId)
				.ifPresent(objektId -> documentsByPlace.computeIfAbsent(objektId, _ -> new ArrayList<>())
					.add(new PlaceDocument(decision.worddokument(), decision.mall(), decision.beslutsDatum(), decision.beslutsId()))));
	}

	private FileList errandFiles(final Collection<Integer> arendeIds, final FileList files) {
		for (final var arendeId : arendeIds) {
			snapshot.filesOf(arendeId).forEach(file -> files.add(file.name(), file.generated() ? DOCUMENTS : ATTACHMENTS));
			decisionsByErrand.getOrDefault(arendeId, List.of()).forEach(decision -> files
				.add(decision.worddokument(), DOCUMENTS)
				.add(decision.bifogadFil(), ATTACHMENTS));
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
	 * @param order the id of the event or decision, which orders documents of the same date
	 */
	private record PlaceDocument(String name, String mall, LocalDate datum, Integer order) {
	}

	/**
	 * What a serving permit holds, interpreted once for the errand, its description and the permit in PartyAssets.
	 *
	 * @param permit       the row of the permit
	 * @param periods      its season and date period
	 * @param servingTimes its serving times, "hh:mm–hh:mm"
	 * @param conditions   its conditions that still apply
	 */
	private record PermitContent(PermitRow permit, PermitPeriods periods, List<String> servingTimes, List<String> conditions) {

		List<String> drinks() {
			return ServingPermits.drinks(permit);
		}

		List<String> servingTo() {
			return ServingPermits.servingTo(permit);
		}

		List<String> scope() {
			return ServingPermits.scope(permit);
		}
	}
}
