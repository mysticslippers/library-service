ALTER TABLE materials
    ADD COLUMN cover_object_key VARCHAR(512),
    ADD COLUMN cover_version UUID,
    ADD CONSTRAINT chk_materials_cover_reference
        CHECK ((cover_object_key IS NULL) = (cover_version IS NULL));

CREATE UNIQUE INDEX uq_materials_cover_object_key
    ON materials (cover_object_key)
    WHERE cover_object_key IS NOT NULL;
