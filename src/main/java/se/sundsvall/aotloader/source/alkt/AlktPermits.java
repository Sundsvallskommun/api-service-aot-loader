package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.DecisionData;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.AssetParameters;
import se.sundsvall.aotloader.source.CandidateTexts;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.ErrandParameters;
import se.sundsvall.aotloader.source.FileList;
import se.sundsvall.aotloader.source.FilePurposes;
import se.sundsvall.aotloader.source.ParameterList;
import se.sundsvall.aotloader.source.PartyRows;
import se.sundsvall.aotloader.source.PermitDecisions;
import se.sundsvall.aotloader.source.PermitFiles;
import se.sundsvall.aotloader.source.PermitInvestigations;
import se.sundsvall.aotloader.source.SourceRules;
import se.sundsvall.aotloader.source.alkt.AlktIndex.PlaceDocument;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ConditionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PermitRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PlaceRow;
import se.sundsvall.aotloader.support.DiaryNumbers;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_ANNEX;
import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_CERTIFICATE;
import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_DRAWING;
import static se.sundsvall.aotloader.Constants.ASSET_PARAMETER_CONDITIONS;
import static se.sundsvall.aotloader.Constants.TAG_DIARY_NUMBER;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_OLDER;
import static se.sundsvall.aotloader.service.model.ReviewRule.DECISION_EXPIRED;
import static se.sundsvall.aotloader.service.model.ReviewRule.DECISION_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.ISSUE_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_INVALID;
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
import static se.sundsvall.aotloader.service.model.ReviewRule.UNPARSEABLE;
import static se.sundsvall.aotloader.service.model.Source.ALKT;
import static se.sundsvall.aotloader.source.CandidateTexts.UNKNOWN_SERVING_PLACE;
import static se.sundsvall.aotloader.source.CandidateTexts.line;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_CONDITION;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_DECISION;
import static se.sundsvall.aotloader.source.alkt.AlktSnapshot.CODE_TYPE_POSITION;

/**
 * Builds the candidates of the current serving permits of AlkT (sections 7.2–7.6), with the review rules that AlkT
 * itself shows.
 */
final class AlktPermits {

	/** The templates of the permit certificate: Sundsvall bevis and the one before it (decision 34). */
	private static final Set<String> CERTIFICATE_TEMPLATES = Set.of("TDBV2", "TDBV");
	/** The templates of the annex of the permit: of Sundsvall, of Timrå, or the older one (decision 34). */
	private static final Set<String> ANNEX_TEMPLATES = Set.of("TDBBS", "TBBT", "TDBB");
	/** The template of the annex for catering, which a permit with catering gets besides the other (decision 34). */
	private static final Set<String> CATERING_ANNEX_TEMPLATES = Set.of("TDBBC");
	/** The templates of the permit decision: of Sundsvall and of Timrå (decision 39). */
	private static final Set<String> DECISION_TEMPLATES = Set.of("TDB", "TBT");

	private final AlktIndex index;
	private final AlktSnapshot snapshot;
	private final LocalDate today;

	AlktPermits(final AlktIndex index) {
		this.index = index;
		this.snapshot = index.snapshot();
		this.today = index.today();
	}

	/**
	 * @param  permit a row of Gällande_Tillstånd with a place
	 * @return        the candidate of the permit
	 */
	Candidate permit(final PermitRow permit) {
		final var place = index.placeOf(permit.objektId());
		final var owner = index.ownerOf(place).orElse(null);
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
		final var errands = issuingErrands.stream().map(snapshot.errands()::get).filter(Objects::nonNull).toList();
		SourceRules.openErrandHits(errands, hits);
		index.diaryNumberHits(permit, hits);
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
		final var premises = place == null ? null : index.place(place);
		final var settings = index.properties().errandType(ErrandType.SERVING_PERMIT);
		final var title = Texts.title("Serveringstillstånd", name);
		final var builder = Candidate.builder(new ItemKey(ALKT, TILLSTAND, String.valueOf(permit.objektId())), ErrandType.SERVING_PERMIT)
			.placeName(placeName)
			.placeNumber(restaurantNumber)
			.ruleHits(hits)
			.holder(holder)
			.place(premises)
			.persons(owner == null ? List.of() : PartyRows.persons(snapshot.personsOf(owner.id()), code -> snapshot.codeText(CODE_TYPE_POSITION, code), today))
			.title(title)
			.investigation(PermitInvestigations.of(title, errands.stream().map(ErrandRow::oppnad).toList(), errands.stream().map(ErrandRow::avslutad).toList(),
				permit.utfardandeDatum(), index.investigationTexts(issuingErrands, permit.notes())))
			.description(description(name, content))
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
				.add("aretRunt", "Året runt", permit.period().aretRunt() ? "Ja" : "Nej")
				.add("sasong", "Säsong", periods.seasonText())
				.add("period", "Period", periods.periodText())
				.add("hogstaAntal", "Högsta antal gäster", permit.servingArea().hogstaAntal())
				.add("sittplatser", "Sittplatser", permit.servingArea().sittplatser())
				.add("serveringslokal", "Serveringslokal", permit.servingArea().serveringslokal())
				.add("villkor", "Villkor", content.conditions())
				.add(ErrandParameters.NOTES, "Noteringar i AlkT", permit.notes())
				.add(ErrandParameters.LATEST_DECISION, "Senaste beslut i AlkT", index.decisionText(decision, true))
				.add(CandidateTexts.migrated(today, ALKT))
				.build())
			.asset(new AssetData(null, "Serveringstillstånd för " + name, permit.utfardandeDatum(),
				ServingPermits.validTo(permit, periods),
				assetParameters(content, holder, premises, restaurantNumber), legalBasisDocuments(permit, decision, files)))
			.files(files.build(), files.assetFiles(), files.details())
			.decision(decisionData(permit, decision, ServingPermits.validTo(permit, periods), content.conditions(), files));
		Optional.ofNullable(subtype).flatMap(settings::subtypeLabel).ifPresent(builder::labelPath);
		return builder.build();
	}

	/**
	 * The rules about the place and its owner: G26, G1, G2, G6, G24, G8 and G7.
	 */
	private void placeAndOwnerHits(final PermitRow permit, final PlaceRow place, final ContactRow owner, final Holder holder, final List<RuleHit> hits) {
		final var rowsOnPlace = index.permitRowsOf(permit.objektId()).size();
		if (rowsOnPlace > 1) {
			hits.add(RuleHit.of(PERMIT_ROWS_ON_PLACE, "%d rader".formatted(rowsOnPlace)));
		}
		if (place == null) {
			hits.add(RuleHit.of(PLACE_MISSING));
			return;
		}
		if (!index.issuedToOwner(permit, place)) {
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
		index.ownerNumberHits(owner, hits);
	}

	private static String ownerText(final PlaceRow place, final ContactRow owner) {
		if (owner != null) {
			return "AgarID " + owner.id();
		}
		return place.agarId() == null ? "serveringsstället saknar ägare" : "AgarID %d finns inte".formatted(place.agarId());
	}

	/**
	 * A restaurant number without the spaces and dots it is sometimes written with on the permit.
	 */
	private static String digitsOf(final String restaurantNumber) {
		return restaurantNumber.replaceAll("[\\h\\v.]", "");
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
	 * @return the decision the permit points to, which its errand gets with the conditions of the permit as terms (decision
	 *         70); null when it points to none
	 */
	private DecisionData decisionData(final PermitRow permit, final DecisionRow decision, final LocalDate validTo, final List<String> conditions, final FileList files) {
		if (decision == null) {
			return null;
		}
		return PermitDecisions.of(decision, snapshot.codeText(CODE_TYPE_DECISION, decision.beslutsTyp()), snapshot.visits().handlerName(decision.beslutsfattare()), permit.utfardandeDatum(),
			validTo, conditions, files, ALKT);
	}

	/**
	 * The rules about the season and the period: G9, G4, G5 and G12.
	 */
	private void periodHits(final PermitRow permit, final PermitPeriods periods, final List<RuleHit> hits) {
		final var period = permit.period();
		periods.problems().forEach(problem -> hits.add(RuleHit.of(UNPARSEABLE, problem)));
		if (periods.periodEnd() != null && periods.periodEnd().isBefore(today)) {
			hits.add(RuleHit.of(PERIOD_PASSED, periods.periodText()));
		}
		if (!period.aretRunt() && Texts.isBlank(Texts.joinWords(period.arligPeriodStart(), period.arligPeriodSlut(), period.periodStart(), period.periodSlut()))) {
			hits.add(RuleHit.of(PERIOD_MISSING));
		}
		if (period.aretRunt() && periods.hasDatePeriod()) {
			hits.add(RuleHit.of(PERIOD_ON_ALL_YEAR, periods.periodText()));
		}
	}

	private static List<String> servingTimes(final PermitRow permit, final List<RuleHit> hits) {
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
	 * The files of the permit: those of the errands that issued it and of the place (decision 7). The files of the place's
	 * events without an errand go to their documentation errands (decision 71). The latest certificate and annexes of the
	 * place, and the drawings and the
	 * permit decisions of the issuing errands, also go on the post in PartyAssets, in that order of precedence (decisions
	 * 34, 35 and 39). A certificate older than the permit, or without a date, may be a former holder's: it goes on the
	 * errand only and hits G29, as does a permit without a certificate. An annex older than the permit, or of catering on a
	 * permit without catering, is left out.
	 */
	private FileList permitFiles(final PermitRow permit, final PlaceRow place, final Set<Integer> issuingErrands, final List<RuleHit> hits) {
		final var files = index.errandFiles(issuingErrands, index.newFileList());
		if (place != null) {
			Stream.of(place.bifogadfil(), place.bifogadfil2()).forEach(name -> files.add(name, ATTACHMENTS, FilePurposes.detailsOf(ALKT, ATTACHMENTS, name, null)));
		}

		index.latestDocument(permit.objektId(), CERTIFICATE_TEMPLATES).ifPresentOrElse(certificate -> {
			files.add(certificate.name(), DOCUMENTS, certificate.details());
			if (isCurrent(certificate, permit)) {
				files.add(certificate.name(), DOCUMENTS, ASSET_CATEGORY_CERTIFICATE);
			} else {
				files.addToErrandOnly(certificate.name(), DOCUMENTS);
				hits.add(RuleHit.of(CERTIFICATE_OLDER, certificate.datum() == null ? "beviset saknar datum"
					: "beviset är från %s, tillståndet utfärdades %s".formatted(certificate.datum(), permit.utfardandeDatum())));
			}
		}, () -> hits.add(RuleHit.of(CERTIFICATE_MISSING)));
		index.latestDocument(permit.objektId(), ANNEX_TEMPLATES)
			.filter(annex -> isCurrent(annex, permit))
			.ifPresent(annex -> files.add(annex.name(), DOCUMENTS, annex.details()).add(annex.name(), DOCUMENTS, ASSET_CATEGORY_ANNEX));
		index.latestDocument(permit.objektId(), CATERING_ANNEX_TEMPLATES)
			.filter(annex -> permit.scope().catering() && isCurrent(annex, permit))
			.ifPresent(annex -> files.add(annex.name(), DOCUMENTS, annex.details()).add(annex.name(), DOCUMENTS, ASSET_CATEGORY_ANNEX));

		for (final var arendeId : issuingErrands) {
			snapshot.filesOf(arendeId).stream()
				// The purpose rather than the name, since a referral answer named as a drawing stays on the errand
				.filter(file -> !file.generated() && FilePurposes.isDrawing(file.details().purpose()))
				.forEach(file -> files.add(file.name(), ATTACHMENTS, ASSET_CATEGORY_DRAWING));
			PermitFiles.markDrawings(files, index.decisionsOf(arendeId));
		}
		if (place != null) {
			Stream.of(place.bifogadfil(), place.bifogadfil2())
				.filter(PermitFiles::isDrawing)
				.forEach(name -> files.add(name, ATTACHMENTS, ASSET_CATEGORY_DRAWING));
		}
		issuingErrands.forEach(arendeId -> index.decisionsOf(arendeId).forEach(decision -> PermitFiles.markPermitDecision(files, decision, DECISION_TEMPLATES)));
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
		final var certificate = index.latestDocument(permit.objektId(), CERTIFICATE_TEMPLATES).map(PlaceDocument::name);
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
			.flag(AssetParameters.SCOPE_ALL_YEAR, permit.period().aretRunt())
			.flag(AssetParameters.SCOPE_ANNUAL_PERIOD, !permit.period().aretRunt() && periods.seasonText() != null)
			.flag(AssetParameters.SCOPE_TASTING, permit.scope().provsmakning())
			.lines(AssetParameters.SCOPE_PERIOD_FROM_TO, Arrays.asList(periods.seasonText(), periods.periodText()))
			.flag(AssetParameters.BEVERAGE_SPIRITS, permit.drinks().spritdrycker())
			.flag(AssetParameters.BEVERAGE_WINE, permit.drinks().vin())
			.flag(AssetParameters.BEVERAGE_BEER, permit.drinks().starkol())
			.flag(AssetParameters.BEVERAGE_OTHER_FERMENTED, permit.drinks().ajaDrycker())
			.text(AssetParameters.SERVING_AREA_DESCRIPTION, permit.servingArea().serveringslokal())
			.text(AssetParameters.SERVING_AREA_SEATS, permit.servingArea().sittplatser())
			.text(AssetParameters.SERVING_AREA_MAX_PERSONS, permit.servingArea().hogstaAntal())
			.lines(AssetParameters.SERVING_HOURS, content.servingTimes())
			.lines(ASSET_PARAMETER_CONDITIONS, content.conditions())
			.text(AssetParameters.CASE_NUMBER, permit.utfardandeDiarienr())
			.text(AssetParameters.DECISION_DATE_AND_CASE_NUMBER, Texts.join(", ", Arrays.asList(SourceValues.format(permit.utfardandeDatum()), permit.utfardandeDiarienr())))
			.text(AssetParameters.DIARY_NUMBER, permit.utfardandeDiarienr())
			.text(AssetParameters.MIGRATED_FROM, ALKT.getDisplayName())
			.build();
	}

	private static String description(final String name, final PermitContent content) {
		final var permit = content.permit();
		final var lines = new ArrayList<String>();
		lines.add("Serveringstillstånd för " + name + ", migrerat från " + ALKT.getDisplayName() + ".");
		lines.add(CandidateTexts.issuedLine(permit.utfardandeDatum(), permit.utfardandeDiarienr()));
		lines.add(line("Drycker", Texts.join(", ", content.drinks())));
		lines.add(line("Servering till", Texts.join(", ", content.servingTo())));
		lines.add(line("Omfattning", Texts.join(", ", content.scope())));
		lines.add(line("Serveringstider", Texts.join(", ", content.servingTimes())));
		lines.add(line("Året runt", permit.period().aretRunt() ? "Ja" : "Nej"));
		lines.add(line("Säsong", content.periods().seasonText()));
		lines.add(line("Period", content.periods().periodText()));
		if (!content.conditions().isEmpty()) {
			lines.add("Villkor:");
			content.conditions().forEach(condition -> lines.add("- " + condition));
		}
		return Texts.join("\n", lines);
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
