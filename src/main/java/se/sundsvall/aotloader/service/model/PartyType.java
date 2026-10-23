package se.sundsvall.aotloader.service.model;

/**
 * The type of a legal id, named as Party knows it, with the external id type it gives a stakeholder in
 * SupportManagement.
 */
public enum PartyType {

	/** An organization number, 10 digits. */
	ENTERPRISE("COMPANY"),

	/** A personal identity number, 12 digits. */
	PRIVATE("PRIVATE");

	private final String externalIdType;

	PartyType(final String externalIdType) {
		this.externalIdType = externalIdType;
	}

	/**
	 * @return the stakeholder's external id type in SupportManagement
	 */
	public String getExternalIdType() {
		return externalIdType;
	}
}
