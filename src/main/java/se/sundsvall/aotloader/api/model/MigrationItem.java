package se.sundsvall.aotloader.api.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.ItemStatus;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.service.model.Step;

/**
 * A row of the status table: a source item and what the migration has created for it.
 *
 * @param source         the source
 * @param kind           the kind of item
 * @param sourceId       the id of the item in the source
 * @param errandType     the type of errand
 * @param status         how far the item has come
 * @param failedStep     the step that failed, for status FEL
 * @param reason         why the item is reviewed, waits or failed
 * @param placeNumber    the restaurant number of the place
 * @param placeName      the name of the place
 * @param municipalityId the municipality the errand and the post in PartyAssets belong to
 * @param errandId       the id of the errand in SupportManagement
 * @param errandNumber   the number of the errand in SupportManagement
 * @param assetId        the asset id of the post in PartyAssets, the same as the errand id
 * @param partyAssetsId  the internal id of the post in PartyAssets, needed to read, change or delete it
 * @param relationId     the id of the relation from the permit errand, for an inspection errand
 * @param lastRunId      the last run that took the item
 * @param modified       when the row last changed
 */
@Schema(description = "A row of the status table")
public record MigrationItem(
	Source source,
	Kind kind,
	String sourceId,
	ErrandType errandType,
	ItemStatus status,
	Step failedStep,
	String reason,
	String placeNumber,
	String placeName,
	String municipalityId,
	String errandId,
	String errandNumber,
	String assetId,
	String partyAssetsId,
	String relationId,
	String lastRunId,
	OffsetDateTime modified) {
}
