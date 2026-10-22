package se.sundsvall.aotloader.integration;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import se.sundsvall.dept44.problem.ThrowableProblem;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * The answers of the target systems that mean something other than an error, such as 404 for a resource that is not
 * there, and the statuses the error decoders let through for them; the decoders make every other status 502.
 */
public final class Fallbacks {

	/**
	 * The statuses the error decoders of SupportManagement and PartyAssets let through: 404, 409 and those that refuse a
	 * file.
	 */
	public static final List<Integer> BYPASSED_STATUSES = Stream.concat(Stream.of(NOT_FOUND.value(), CONFLICT.value()), FileRefusal.STATUSES.stream()).sorted().toList();

	private Fallbacks() {}

	/**
	 * @param  status           the status that means the fallback
	 * @param  fallback         what an answer with the status means
	 * @param  call             the call
	 * @return                  what the call returns, or the fallback when the target system answers with the status
	 * @throws ThrowableProblem when the call fails with any other status
	 */
	public static <T> T unless(final HttpStatus status, final T fallback, final Supplier<T> call) {
		try {
			return call.get();
		} catch (final ThrowableProblem problem) {
			if (problem.getStatus() == status) {
				return fallback;
			}
			throw problem;
		}
	}
}
