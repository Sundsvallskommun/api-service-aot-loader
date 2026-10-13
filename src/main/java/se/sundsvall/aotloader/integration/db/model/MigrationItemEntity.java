package se.sundsvall.aotloader.integration.db.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Objects;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.ItemStatus;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.service.model.Step;

import static jakarta.persistence.GenerationType.IDENTITY;

/**
 * A row of the status table: one per source item, with what the migration has created for it (section 8).
 */
@Entity
@Table(name = "migration_item")
public class MigrationItemEntity {

	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "id")
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "source", nullable = false, updatable = false)
	private Source source;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, updatable = false)
	private Kind kind;

	@Column(name = "source_id", nullable = false, updatable = false)
	private String sourceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "errand_type")
	private ErrandType errandType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private ItemStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "failed_step")
	private Step failedStep;

	@Column(name = "reason")
	private String reason;

	@Column(name = "place_name")
	private String placeName;

	@Column(name = "place_number")
	private String placeNumber;

	@Column(name = "municipality_id")
	private String municipalityId;

	@Column(name = "errand_id")
	private String errandId;

	@Column(name = "errand_number")
	private String errandNumber;

	@Column(name = "asset_id")
	private String assetId;

	@Column(name = "party_assets_id")
	private String partyAssetsId;

	@Column(name = "relation_id")
	private String relationId;

	@Column(name = "last_run_id")
	private String lastRunId;

	@Column(name = "created", nullable = false, updatable = false)
	private OffsetDateTime created;

	@Column(name = "modified", nullable = false)
	private OffsetDateTime modified;

	public Long getId() {
		return id;
	}

	public void setId(final Long id) {
		this.id = id;
	}

	public Source getSource() {
		return source;
	}

	public void setSource(final Source source) {
		this.source = source;
	}

	public Kind getKind() {
		return kind;
	}

	public void setKind(final Kind kind) {
		this.kind = kind;
	}

	public String getSourceId() {
		return sourceId;
	}

	public void setSourceId(final String sourceId) {
		this.sourceId = sourceId;
	}

	public ErrandType getErrandType() {
		return errandType;
	}

	public void setErrandType(final ErrandType errandType) {
		this.errandType = errandType;
	}

	public ItemStatus getStatus() {
		return status;
	}

	public void setStatus(final ItemStatus status) {
		this.status = status;
	}

	public Step getFailedStep() {
		return failedStep;
	}

	public void setFailedStep(final Step failedStep) {
		this.failedStep = failedStep;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(final String reason) {
		this.reason = reason;
	}

	public String getPlaceName() {
		return placeName;
	}

	public void setPlaceName(final String placeName) {
		this.placeName = placeName;
	}

	public String getPlaceNumber() {
		return placeNumber;
	}

	public void setPlaceNumber(final String placeNumber) {
		this.placeNumber = placeNumber;
	}

	public String getMunicipalityId() {
		return municipalityId;
	}

	public void setMunicipalityId(final String municipalityId) {
		this.municipalityId = municipalityId;
	}

	public String getErrandId() {
		return errandId;
	}

	public void setErrandId(final String errandId) {
		this.errandId = errandId;
	}

	public String getErrandNumber() {
		return errandNumber;
	}

	public void setErrandNumber(final String errandNumber) {
		this.errandNumber = errandNumber;
	}

	public String getAssetId() {
		return assetId;
	}

	public void setAssetId(final String assetId) {
		this.assetId = assetId;
	}

	public String getPartyAssetsId() {
		return partyAssetsId;
	}

	public void setPartyAssetsId(final String partyAssetsId) {
		this.partyAssetsId = partyAssetsId;
	}

	public String getRelationId() {
		return relationId;
	}

	public void setRelationId(final String relationId) {
		this.relationId = relationId;
	}

	public String getLastRunId() {
		return lastRunId;
	}

	public void setLastRunId(final String lastRunId) {
		this.lastRunId = lastRunId;
	}

	public OffsetDateTime getCreated() {
		return created;
	}

	public void setCreated(final OffsetDateTime created) {
		this.created = created;
	}

	public OffsetDateTime getModified() {
		return modified;
	}

	public void setModified(final OffsetDateTime modified) {
		this.modified = modified;
	}

	@Override
	public boolean equals(final Object o) {
		return this == o || (o instanceof final MigrationItemEntity other && Objects.equals(id, other.id));
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(id);
	}

	@Override
	public String toString() {
		return "MigrationItemEntity [id=" + id + ", source=" + source + ", kind=" + kind + ", sourceId=" + sourceId + ", errandType=" + errandType + ", status=" + status + ", failedStep=" + failedStep + ", reason=" + reason + ", placeName="
			+ placeName + ", placeNumber=" + placeNumber + ", municipalityId=" + municipalityId + ", errandId=" + errandId + ", errandNumber=" + errandNumber + ", assetId=" + assetId + ", partyAssetsId=" + partyAssetsId + ", relationId=" + relationId
			+ ", lastRunId=" + lastRunId
			+ ", created=" + created + ", modified=" + modified + "]";
	}
}
