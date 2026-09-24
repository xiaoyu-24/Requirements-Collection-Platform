-- The initial schema inherited the database default MyISAM engine, which ignores foreign keys.
ALTER TABLE requirements ENGINE = InnoDB;

ALTER TABLE requirements
    ADD CONSTRAINT fk_requirements_department FOREIGN KEY (department_id) REFERENCES dictionary_items(id),
    ADD CONSTRAINT fk_requirements_type FOREIGN KEY (type_id) REFERENCES dictionary_items(id);
