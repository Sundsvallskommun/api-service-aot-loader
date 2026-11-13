package se.sundsvall.aotloader.configuration;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfiguration {

	/** The rules that compare with today's date use the Swedish date, whatever zone the computer is set to. */
	public static final ZoneId SWEDISH_TIME = ZoneId.of("Europe/Stockholm");

	@Bean
	Clock clock() {
		return Clock.system(SWEDISH_TIME);
	}
}
