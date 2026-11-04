package se.sundsvall.aotloader.source;

import java.util.Set;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.Source;

/**
 * Reads a source database and turns its rows into candidates of the common model.
 */
public interface SourceReader {

	/**
	 * @return the source the reader reads
	 */
	Source source();

	/**
	 * @return the kinds of item the source has
	 */
	Set<Kind> kinds();

	/**
	 * Reads the source once and returns the candidates of the kinds asked for, the kinds in the order of {@link Kind} and
	 * each kind in the order of its id.
	 *
	 * @param  kinds the kinds to return
	 * @return       the candidates and the notices
	 */
	ReaderResult read(Set<Kind> kinds);
}
