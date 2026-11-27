package se.sundsvall.aotloader.source;

import java.util.Collection;
import java.util.Set;
import java.util.regex.Pattern;
import se.sundsvall.aotloader.support.FileNames;

import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_DECISION;
import static se.sundsvall.aotloader.Constants.ASSET_CATEGORY_DRAWING;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;

/**
 * Tells which files of a permit or a notification go on its post in PartyAssets besides the certificate (decisions 34,
 * 35, 38 and 39).
 */
public final class PermitFiles {

	private static final String DRAWING_WORD = "ritning";
	private static final Pattern CERTIFICATE_NAME = Pattern.compile("tillst[aå]nds[\\s_-]*bevis");
	private static final Pattern DECISION_NAME = Pattern.compile("tillst[aå]nd|beslut|bevis");
	// Words of documents about a permit rather than of the permit, such as a registration certificate from Bolagsverket
	private static final Pattern OTHER_DOCUMENT_NAME = Pattern.compile("underlag|utredning|remiss|yttrande|kommunicering|ans[oö]kan|beg[aä]ran|komplettering|mottagning|registrering|utdrag");

	private PermitFiles() {}

	/**
	 * @return true for an attachment whose name tells it is a permit certificate, e.g. "Tillståndsbevis 2023.pdf" or
	 *         tillstands_bevis.pdf, and not another certificate such as Mottagningsbevis.pdf
	 */
	public static boolean isCertificate(final String name) {
		return name != null && CERTIFICATE_NAME.matcher(FileNames.normalized(name)).find();
	}

	/**
	 * @return true for an attachment whose name tells it is a permit, a decision or a certificate, e.g. "Beslut 2019.pdf"
	 *         or Tillståndsbevis.pdf, and not a document about one, e.g. Beslutsunderlag.pdf, "Remiss tillstånd.pdf" or
	 *         Registreringsbevis.pdf
	 */
	public static boolean isDecision(final String name) {
		if (name == null) {
			return false;
		}
		final var lowerCase = FileNames.normalized(name);
		return DECISION_NAME.matcher(lowerCase).find() && !OTHER_DOCUMENT_NAME.matcher(lowerCase).find();
	}

	/**
	 * @return true for a file whose name tells it is a drawing, e.g. Planritning.pdf or "ritning våning 2.pdf"
	 */
	public static boolean isDrawing(final String name) {
		return name != null && FileNames.normalized(name).contains(DRAWING_WORD);
	}

	/**
	 * Marks the files of a decision that are the permit decision for the post (decision 39): its document when it is made
	 * from one of the templates, and its attachment when the name tells it is a permit, a decision or a certificate. The
	 * others, such as an investigation, a referral answer or a receipt letter, stay on the errand only.
	 *
	 * @param templates the templates of the documents that are permit decisions
	 */
	public static void markPermitDecision(final FileList files, final SourceDecision decision, final Set<String> templates) {
		if (isTemplate(decision.mall(), templates)) {
			files.add(decision.worddokument(), DOCUMENTS, ASSET_CATEGORY_DECISION);
		}
		if (isDecision(decision.bifogadfil())) {
			files.add(decision.bifogadfil(), ATTACHMENTS, ASSET_CATEGORY_DECISION);
		}
	}

	/**
	 * Marks the attachments of the decisions whose names tell they are drawings for the post, in the category of drawings.
	 */
	public static void markDrawings(final FileList files, final Collection<? extends SourceDecision> decisions) {
		decisions.stream()
			.map(SourceDecision::bifogadfil)
			.filter(PermitFiles::isDrawing)
			.forEach(name -> files.add(name, ATTACHMENTS, ASSET_CATEGORY_DRAWING));
	}

	/**
	 * @param  templates the codes of the templates
	 * @return           true when the code of a document template is one of the codes, regardless of case
	 */
	public static boolean isTemplate(final String code, final Set<String> templates) {
		return CodeTexts.isOneOf(code, templates);
	}
}
