
-- src/clojure_stats/output.sql


-- :name create-form-type :!

CREATE TYPE form_type AS ENUM ('symbol', 'list', 'var', 'coll', 'data', 'other');


-- :name create-tables :!
CREATE SEQUENCE form_id_serial;
-- ;;
CREATE SEQUENCE file_id_serial;
-- ;;
CREATE TABLE files (file_id INTEGER PRIMARY KEY DEFAULT nextval('file_id_serial'),
	file_name VARCHAR);
-- ;;
CREATE TABLE forms (form_id INTEGER PRIMARY KEY DEFAULT nextval('form_id_serial'),
	form_type form_type,
	form VARCHAR,
	resolved_symbol VARCHAR,
	meta VARCHAR,
	file_id INTEGER,
	file_line INTEGER,
	file_column INTEGER,
	FOREIGN KEY (file_id) REFERENCES files(file_id));


-- :name insert-files :!
INSERT INTO files (file_name)
VALUES :t*:vals;

-- :name insert-forms :!

INSERT INTO forms (file_id, form_type, form, resolved_symbol, meta, file_line, file_column)
SELECT files.file_id, form_type, form, resolved_symbol, meta, file_line, file_column
FROM (VALUES :t*:vals) form_values(file_name, form_type, form, resolved_symbol, meta, file_line, file_column)
	LEFT OUTER JOIN files ON (form_values.file_name = files.file_name) ;

