package se.sundsvall.aotloader.source.alkol;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import se.sundsvall.aotloader.service.model.AssetData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.DecisionData;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.FileReference.FileFolder;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Person;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.AssetParameters;
import se.sundsvall.aotloader.source.CandidateTexts;
import se.sundsvall.aotloader.source.CodeTexts;
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
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.PermitViewRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.SalesPlaceRow;
import se.sundsvall.aotloader.support.SourceValues;
import se.sundsvall.aotloader.support.Texts;

import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_CERTIFICATE;
import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_DRAWING;
import static se.sundsvall.aotloader.Constants.TAG_DIARY_NUMBER;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.ReviewRule.APPLICANT_NOT_OWNER;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_OLDER;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.ISSUE_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERMIT_ENDED;
import static se.sundsvall.aotloader.service.model.ReviewRule.PLACE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.SALES_PLACE_CEASED;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNPARSEABLE;
import static se.sundsvall.aotloader.service.model.Source.ALKOL;
import static se.sundsvall.aotloader.source.CandidateTexts.UNKNOWN_SALES_PLACE;
import static se.sundsvall.aotloader.source.CandidateTexts.line;
import static se.sundsvall.aotloader.source.alkol.AlkolIndex.DECISION_ORDER;
import static se.sundsvall.aotloader.source.alkol.AlkolIndex.dateText;
import static se.sundsvall.aotloader.source.alkol.AlkolIndex.isAfter;
import static se.sundsvall.aotloader.source.alkol.AlkolIndex.issued;
import static se.sundsvall.aotloader.source.alkol.PermitKind.GRANTING_DECISIONS;

/**
 * Builds the candidates of the tobacco permits of AlkOL, one per sales place in the view FS_GT_Tobak, from the latest
 * granting decision of the place and its errand.
 */
final class AlkolPermits {

	/**
	 * The templates of the tobacco permit certificate, of Sundsvall and of Timrå, made at the granting decision (decision
	 * 34).
	 */
	private static final Set<String> CERTIFICATE_TEMPLATES = Set.of("TILLSTT", "TILLTT");
	// The products are notified on the sales place, not on the permit (decision 68)
	private static final String NOTIFIED_PRODUCTS = "Anmälda produkter på försäljningsstället";

	private final AlkolIndex index;
	private final AlkolNotifications notifications;
	private final AlkolSnapshot snapshot;
	private final LocalDate today;

	/**
	 * @param notifications the notifications of the sales places, whose dates the permit shows
	 */
	AlkolPermits(final AlkolIndex index, final AlkolNotifications notifications) {
		this.index = index;
		this.notifications = notifications;
		this.snapshot = index.snapshot();
		this.today = index.today();
	}

	/**
	 * @param  fsId  the sales place in the view FS_GT_Tobak
	 * @param  place the sales place, null when FörsäljningsStällen does not have it
	 * @return       the candidate of the tobacco permit of the place
	 */
	Candidate permit(final Integer fsId, final SalesPlaceRow place) {
		final var owner = index.ownerOf(place);
		final var grant = index.latestGrant(fsId).orElse(null);
		final var grantErrand = grant == null ? null : index.errand(grant.arendeId());
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
		final var products = place == null ? List.<String>of() : Product.notifiedOn(place).stream().map(product -> notifiedProduct(place, product)).toList();
		final var kinds = permitKinds(view, grant);
		final var kindsText = Texts.join(", ", kinds.stream().map(PermitKind::text).toList());
		final var holder = PartyRows.holder(owner, today);
		final var premises = place == null ? null : index.place(place);
		final var files = permitFiles(fsId, grant, grantErrand, hits);
		final var title = Texts.title("Tobakstillstånd", name);

		return Candidate.builder(new ItemKey(ALKOL, TILLSTAND, String.valueOf(fsId)), ErrandType.TOBACCO_PERMIT)
			.placeName(placeName)
			.ruleHits(hits)
			.holder(holder)
			.place(premises)
			.persons(persons(owner))
			.title(title)
			.investigation(permitInvestigation(title, grant, grantErrand, notes))
			.description(Texts.join("\n", Arrays.asList(
				"Tobakstillstånd för " + name + ".",
				line("Tillståndstyp", kindsText),
				CandidateTexts.issuedLine(issued, diaryNumber),
				line(NOTIFIED_PRODUCTS + " (gäller hela stället, inte bara detta ärende)", Texts.join("; ", products)))))
			.labelPaths(index.properties().errandType(ErrandType.TOBACCO_PERMIT).labels())
			.externalTag(TAG_DIARY_NUMBER, diaryNumber)
			.parameters(new ParameterList()
				.add(ErrandParameters.ALKOL_FS_ID, "Id i AlkOL", String.valueOf(fsId))
				.add("tillstandstyp", "Tillståndstyp", kindsText)
				.add(ErrandParameters.ISSUED, "Utfärdat", SourceValues.format(issued))
				.add(ErrandParameters.DIARY_NUMBER, "Diarienummer i AlkOL", diaryNumber)
				.add(ErrandParameters.HANDLERS, "Handläggare i AlkOL", grantErrand == null ? List.of() : snapshot.visits().handlerNames(grantErrand.handlaggare()))
				.add("tidigareBeviljanden", "Tidigare beviljanden", earlierGrantTexts(fsId, grant))
				.add(ErrandParameters.LATEST_DECISION, "Senaste beslut i AlkOL", latestDecisionText(fsId, grant))
				.add("anmaldaProdukter", NOTIFIED_PRODUCTS, products)
				.add(ErrandParameters.NOTES, "Noteringar i AlkOL", notes)
				.add(CandidateTexts.migrated(today, ALKOL))
				.build())
			.asset(new AssetData(kindsText == null ? null : kindsText.toLowerCase(Locale.ROOT), "Tobakstillstånd för " + name, issued, null,
				assetParameters(holder, premises, kinds, diaryNumber), legalBasisDocuments(fsId, grant, files)))
			.files(files.build(), files.assetFiles(), files.details())
			.decision(grantDecision(grant, issued, files))
			.build();
	}

	/**
	 * @return the latest granting decision, which the errand of the permit gets (decision 70); null when there is none
	 */
	private DecisionData grantDecision(final DecisionRow grant, final LocalDate issued, final FileList files) {
		if (grant == null) {
			return null;
		}
		return PermitDecisions.of(grant, snapshot.codes().decisionType(grant.beslutsTyp()), snapshot.codes().userName(grant.beslutsfattare()), issued, null, List.of(), files, ALKOL);
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
		index.ownerHits(place, owner, hits);
		if (place != null && place.upphort()) {
			hits.add(RuleHit.of(SALES_PLACE_CEASED));
		}
		grantHits(fsId, grant, grantErrand, owner, hits);
		SourceRules.openErrandHits(grantErrand == null ? List.of() : List.of(grantErrand), hits);
		if (grant != null && issued == null) {
			hits.add(RuleHit.of(ISSUE_DATE_MISSING));
		}
		return hits;
	}

	/**
	 * The rules about the granting decision: G15, G18, G9 for a later grant without a date, G16 and G19.
	 */
	private void grantHits(final Integer fsId, final DecisionRow grant, final ErrandRow grantErrand, final ContactRow owner, final List<RuleHit> hits) {
		if (grant == null) {
			hits.add(RuleHit.of(DIARY_NUMBER_MISSING, "inget beviljande beslut hittades"));
			return;
		}
		index.endingAfter(fsId, grant).ifPresent(ending -> hits.add(RuleHit.of(PERMIT_ENDED, Texts.joinWords(ending.beslutsTyp(), dateText(ending)))));
		index.decisionsOfPlace(fsId).stream()
			.filter(decision -> decision.beslutad() == null && CodeTexts.isOneOf(decision.beslutsTyp(), GRANTING_DECISIONS) && isAfter(decision, grant))
			.findFirst()
			.ifPresent(undated -> hits.add(RuleHit.of(UNPARSEABLE, "det beviljande beslutet %d saknar datum och kan vara det senaste".formatted(undated.beslutsId()))));
		index.diaryNumberHits(grantErrand, hits);
		if (index.applicantIsNotOwner(grantErrand, owner)) {
			hits.add(RuleHit.of(APPLICANT_NOT_OWNER));
		}
	}

	/**
	 * @return the product with the date its notification has, "Folköl, anmäld 2015-01-20", or "(uppskattat)" after an
	 *         estimated date; the product alone when the notification has no date
	 */
	private String notifiedProduct(final SalesPlaceRow place, final Product product) {
		final var notified = notifications.notifiedDate(place, product).text();
		return notified == null ? product.parameterText() : product.parameterText() + ", anmäld " + notified;
	}

	private List<Person> persons(final ContactRow owner) {
		return owner == null ? List.of() : PartyRows.persons(snapshot.persons().getOrDefault(owner.id(), List.of()), snapshot.codes()::position, today);
	}

	/**
	 * @param  shownNote the note of the place, which the permit already shows as a parameter; null when none
	 * @return           the investigation of the tobacco permit from the errand of its latest grant (decision 46), without
	 *                   the note already shown
	 */
	private InvestigationData permitInvestigation(final String title, final DecisionRow grant, final ErrandRow grantErrand, final String shownNote) {
		return PermitInvestigations.of(title, Stream.ofNullable(grantErrand).map(ErrandRow::oppnad).toList(),
			Stream.ofNullable(grantErrand).map(ErrandRow::avslutad).toList(), grant == null ? null : grant.beslutsDatum(),
			grantErrand == null ? InvestigationTexts.NONE : index.investigationTexts(grantErrand, shownNote));
	}

	/**
	 * The files of the tobacco permit: those of the errand of the latest granting decision (decision 7). The files of the
	 * place's events without an errand go to their notification or their documentation errand (decisions 37 and 71). The
	 * certificate, and the drawings and
	 * the permit decisions of that errand, also go on the post in PartyAssets, in that order of precedence (decisions 34,
	 * 35 and 39). A certificate of an earlier grant than the latest may be a former holder's: it goes on the errand only
	 * and hits G29, as does a permit without a certificate.
	 */
	private FileList permitFiles(final Integer fsId, final DecisionRow grant, final ErrandRow grantErrand, final List<RuleHit> hits) {
		final var arendeIds = grantErrand == null ? List.<Integer>of() : List.of(grantErrand.arendeId());
		final var files = index.addErrandFiles(arendeIds, index.newFileList());

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
			index.eventsOfErrand(arendeId).stream()
				.filter(AlkolIndex::attachesDrawing)
				.forEach(event -> files.add(event.bifogadfil(), ATTACHMENTS, ASSET_CATEGORY_DRAWING));
			PermitFiles.markDrawings(files, index.decisionsOfErrand(arendeId));
			index.decisionsOfErrand(arendeId).forEach(decision -> PermitFiles.markPermitDecision(files, decision, CERTIFICATE_TEMPLATES));
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
		return index.decisionsOfPlace(fsId).stream()
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
	 * @return the granting decisions before the latest one, as "date, diary number"
	 */
	private List<String> earlierGrantTexts(final Integer fsId, final DecisionRow grant) {
		if (grant == null) {
			return List.of();
		}
		return index.decisionsOfPlace(fsId).stream()
			.filter(decision -> CodeTexts.isOneOf(decision.beslutsTyp(), GRANTING_DECISIONS))
			.filter(decision -> isAfter(grant, decision))
			.sorted(DECISION_ORDER)
			.map(decision -> Texts.join(", ", Arrays.asList(dateText(decision), Optional.ofNullable(index.errand(decision.arendeId())).map(ErrandRow::diarieNr).orElse(null))))
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
		return index.decisionsOfPlace(fsId).stream()
			.filter(decision -> isAfter(decision, grant))
			.max(DECISION_ORDER)
			.map(index::decisionText)
			.orElse(null);
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

	/**
	 * The parameters of the tobacco permit, under the keys of the AoT solution (section 7.4). The kinds of sale are the
	 * kinds of the permit.
	 */
	private static Map<String, String> assetParameters(final Holder holder, final Place premises, final List<PermitKind> kinds, final String diaryNumber) {
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
}
