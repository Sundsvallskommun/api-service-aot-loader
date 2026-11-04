-- The rest of the environment the status table holds for: the namespace of the errands, and the PartyAssets and the
-- relation service whose ids it keeps. A database bound before these were recorded gets them at its next run.
ALTER TABLE migration_target ADD COLUMN namespace VARCHAR(32);
ALTER TABLE migration_target ADD COLUMN party_assets_url VARCHAR(500);
ALTER TABLE migration_target ADD COLUMN relation_url VARCHAR(500);
