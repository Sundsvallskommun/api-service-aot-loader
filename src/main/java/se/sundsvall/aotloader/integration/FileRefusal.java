package se.sundsvall.aotloader.integration;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;

/**
 * The answers of SupportManagement and PartyAssets to an upload that refuse the file itself. The file is then listed
 * (G14) and the item goes on; any other error fails the step, and the next run tries the file again. The error decoders
 * of the clients let these statuses through, so that they can be told apart.
 */
public final class FileRefusal {

	/** The statuses the error decoders let through, so that a refusal can be told from another error. */
	public static final Set<Integer> STATUSES = Set.of(BAD_REQUEST.value(), CONTENT_TOO_LARGE.value(), UNSUPPORTED_MEDIA_TYPE.value(), UNPROCESSABLE_CONTENT.value());

	private static final Set<Integer> REFUSING = Set.of(CONTENT_TOO_LARGE.value(), UNSUPPORTED_MEDIA_TYPE.value(), UNPROCESSABLE_CONTENT.value());
	// The title PartyAssets gives an empty file ("Invalid file") and a name that is too long ("Invalid file name")
	private static final String PARTY_ASSETS_INVALID_FILE = "invalid file";

	private FileRefusal() {}

	/**
	 * @return true when SupportManagement refuses the file. Its 400 is no refusal: it answers so when it cannot read the
	 *         upload, e.g. when the stream breaks or a size limit of its own is passed, which another run may get past.
	 */
	public static boolean isRefusedBySupportManagement(final ThrowableProblem problem) {
		return REFUSING.contains(statusOf(problem));
	}

	/**
	 * @return true when PartyAssets refuses the file: 413, 415 or 422, or 400 for an empty file or a name that is too long.
	 *         Its 400 for a post that takes no files, e.g. an EXPIRED one, is no refusal.
	 */
	public static boolean isRefusedByPartyAssets(final ThrowableProblem problem) {
		final var status = statusOf(problem);
		return REFUSING.contains(status) || (status == BAD_REQUEST.value() && Optional.ofNullable(problem.getDetail())
			.map(detail -> detail.toLowerCase(Locale.ROOT).contains(PARTY_ASSETS_INVALID_FILE))
			.orElse(false));
	}

	private static int statusOf(final ThrowableProblem problem) {
		return problem.getStatus() == null ? 0 : problem.getStatus().value();
	}
}
