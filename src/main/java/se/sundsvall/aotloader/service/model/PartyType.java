package se.sundsvall.aotloader.service.model;

/**
 * The type of a legal id, as Party and SupportManagement know it.
 */
public enum PartyType {

	/** An organization number, 10 digits. */
	ENTERPRISE,

	/** A personal identity number, 12 digits. */
	PRIVATE
}
