-- The items a run or a cleanup is narrowed to, separated by commas, so that a selection of items is taken in one run
-- with one report and one result file. Null for a run of every item and for the run of a single item (item_key).
ALTER TABLE migration_run ADD COLUMN item_keys CLOB;
