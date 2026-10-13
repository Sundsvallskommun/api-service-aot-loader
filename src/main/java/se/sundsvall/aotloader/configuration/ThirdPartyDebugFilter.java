package se.sundsvall.aotloader.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

/**
 * Keeps DEBUG and TRACE of every logger but the migration's own out of the log, whatever level they are set to (K6).
 */
public final class ThirdPartyDebugFilter extends TurboFilter {

	// The libraries write whole requests, responses and objects at those levels, with names and personal identity numbers
	// of the permit holders, and the secret of the client in the token requests
	static final String OWN_LOGGERS = "se.sundsvall.aotloader.";

	/**
	 * Adds a filter to the logger context, unless it has one.
	 *
	 * @param  context the logger context
	 * @return         the filter of the context
	 */
	static ThirdPartyDebugFilter register(final LoggerContext context) {
		synchronized (context) {
			return context.getTurboFilterList().stream()
				.filter(ThirdPartyDebugFilter.class::isInstance)
				.map(ThirdPartyDebugFilter.class::cast)
				.findFirst()
				.orElseGet(() -> {
					final var filter = new ThirdPartyDebugFilter();
					filter.setContext(context);
					filter.start();
					context.addTurboFilter(filter);
					return filter;
				});
		}
	}

	@Override
	public FilterReply decide(final Marker marker, final Logger logger, final Level level, final String format, final Object[] params, final Throwable t) {
		return level != null && level.toInt() < Level.INFO_INT && !logger.getName().startsWith(OWN_LOGGERS) ? FilterReply.DENY : FilterReply.NEUTRAL;
	}
}
