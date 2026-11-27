package se.sundsvall.aotloader.integration.relation.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("integration.relation")
public record RelationProperties(int connectTimeout, int readTimeout) {
}
