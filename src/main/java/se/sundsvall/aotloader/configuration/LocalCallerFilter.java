package se.sundsvall.aotloader.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets only the computer that runs the migration call it. The server listens on the loopback address only, but a web
 * page in the browser of that computer can still reach it: through a name of its own that resolves to the loopback
 * address, or by posting a form to it. A request is refused with 403 when its Host is not a loopback name or address,
 * and a request that may change something also when its Origin is another than this computer or the browser tells
 * that another site sent it. The Host is read from the header as the caller sent it, never from X-Forwarded-Host or
 * Forwarded, which a page can set; the server does not honour them either (server.forward-headers-strategy).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LocalCallerFilter extends OncePerRequestFilter {

	private static final Logger LOG = LoggerFactory.getLogger(LocalCallerFilter.class);
	private static final Set<String> LOOPBACK_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]", "::1");
	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
	private static final Set<String> SAFE_FETCH_SITES = Set.of("same-origin", "none");
	private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";

	@Override
	protected void doFilterInternal(final HttpServletRequest request, final HttpServletResponse response, final FilterChain chain) throws ServletException, IOException {
		if (!isLoopback(hostOf(request.getHeader(HttpHeaders.HOST)))) {
			refuse(response, "Anropet ska gå till den här datorn med localhost eller 127.0.0.1");
			return;
		}
		if (!SAFE_METHODS.contains(request.getMethod()) && !isFromThisComputer(request.getHeader(HttpHeaders.ORIGIN), request.getHeader(SEC_FETCH_SITE))) {
			refuse(response, "Anropet kommer från en annan webbplats");
			return;
		}
		chain.doFilter(request, response);
	}

	/**
	 * @return true when neither the origin nor the browser tells that another site sent the request; a request without
	 *         either, as from curl, is from this computer, since the server listens on the loopback address only
	 */
	static boolean isFromThisComputer(final String origin, final String fetchSite) {
		if (fetchSite != null && !SAFE_FETCH_SITES.contains(fetchSite.toLowerCase(Locale.ROOT))) {
			return false;
		}
		if (origin == null) {
			return true;
		}
		try {
			final var uri = new URI(origin);
			return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) && isLoopback(uri.getHost());
		} catch (final URISyntaxException _) {
			return false;
		}
	}

	static boolean isLoopback(final String host) {
		return host != null && LOOPBACK_HOSTS.contains(host.toLowerCase(Locale.ROOT));
	}

	/**
	 * @return the name or address of a Host header, without its port, e.g. "[::1]" of "[::1]:8080"; null without one
	 */
	static String hostOf(final String hostHeader) {
		if (hostHeader == null) {
			return null;
		}
		final var host = hostHeader.strip();
		if (host.startsWith("[")) {
			final var end = host.indexOf(']');
			return end < 0 ? host : host.substring(0, end + 1);
		}
		final var colon = host.indexOf(':');
		return colon < 0 ? host : host.substring(0, colon);
	}

	private static void refuse(final HttpServletResponse response, final String detail) throws IOException {
		// Without the headers, which the caller chose
		LOG.warn("Refused a request whose Host or Origin is not this computer");
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8);
		response.getWriter().write("{\"title\":\"Forbidden\",\"status\":403,\"detail\":\"" + detail + "\"}");
	}
}
