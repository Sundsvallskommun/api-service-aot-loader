package se.sundsvall.aotloader.integration.partyassets.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("integration.party-assets")
public record PartyAssetsProperties(int connectTimeout, int readTimeout) {
}
