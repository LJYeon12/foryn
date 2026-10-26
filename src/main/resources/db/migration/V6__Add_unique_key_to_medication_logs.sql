ALTER TABLE medication_logs
    ADD CONSTRAINT UK_DATE_BUNDLE UNIQUE (date, medication_bundle_id);