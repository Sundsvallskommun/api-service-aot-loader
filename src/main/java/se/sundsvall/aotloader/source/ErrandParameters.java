package se.sundsvall.aotloader.source;

/**
 * The keys of the errand parameters that more than one kind of item gets (section 7.3.3). A parameter that tells what
 * the source says has the source in its name shown, as "Diarienummer i AlkT".
 */
public final class ErrandParameters {

	public static final String DIARY_NUMBER = "diarienummer";
	public static final String ISSUED = "utfardat";
	public static final String NOTES = "noteringar";
	public static final String LATEST_DECISION = "senasteBeslut";
	public static final String AREA = "omrade";
	public static final String ALKOL_FS_ID = "alkolFsId";
	public static final String EVENT_LOG = "handelselogg";
	public static final String HANDLERS = "handlaggare";
	/** Who made an inspection visit, the field Kontrollant of AlkT (decision 66). */
	public static final String INSPECTORS = "kontrollant";

	private ErrandParameters() {}
}
