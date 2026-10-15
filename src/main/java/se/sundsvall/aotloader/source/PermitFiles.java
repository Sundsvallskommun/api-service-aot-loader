package se.sundsvall.aotloader.source;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tells which files of a permit go on its post in PartyAssets besides the certificate (decisions 34 and 35).
 */
public final class PermitFiles {

	private static final String DRAWING_WORD = "ritning";
	private static final Pattern CERTIFICATE_NAME = Pattern.compile("tillst[aå]nds[\\s_-]*bevis");

	private PermitFiles() {}

	/**
	 * @return true for an attachment whose name tells it is a permit certificate, e.g. "Tillståndsbevis 2023.pdf" or
	 *         tillstands_bevis.pdf, and not another certificate such as Mottagningsbevis.pdf
	 */
	public static boolean isCertificate(final String name) {
		return name != null && CERTIFICATE_NAME.matcher(name.toLowerCase(Locale.ROOT)).find();
	}

	/**
	 * @return true for a file whose name tells it is a drawing, e.g. Planritning.pdf or "ritning våning 2.pdf"
	 */
	public static boolean isDrawing(final String name) {
		return name != null && name.toLowerCase(Locale.ROOT).contains(DRAWING_WORD);
	}

	/**
	 * @param  templates the codes of the templates, in upper case
	 * @return           true when the code of a document template is one of the codes, regardless of case
	 */
	public static boolean isTemplate(final String code, final Set<String> templates) {
		return code != null && templates.contains(code.strip().toUpperCase(Locale.ROOT));
	}
}
