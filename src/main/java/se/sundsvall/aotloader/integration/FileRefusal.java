package se.sundsvall.aotloader.integration;

import java.util.Set;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONTENT_TOO_LARGE;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;

/**
 * The answers of SupportManagement and PartyAssets to an upload that refuse the file itself. The file is then listed
 * (G14) and the item goes on; any other error fails the step. The error decoders of the clients let them through, so
 * that they can be told apart.
 */
public final class FileRefusal {

	public static final Set<Integer> STATUSES = Set.of(BAD_REQUEST.value(), CONTENT_TOO_LARGE.value(), UNSUPPORTED_MEDIA_TYPE.value(), UNPROCESSABLE_CONTENT.value());

	private FileRefusal() {}

	/**
	 * @return true when the answer refuses the file itself
	 */
	public static boolean isRefusal(final ThrowableProblem problem) {
		return problem.getStatus() != null && STATUSES.contains(problem.getStatus().value());
	}
}
