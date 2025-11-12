
-- src/clojure_stats/output.sql


-- :name create-form-type :!

CREATE TYPE form_type AS ENUM ('symbol', 'list', 'var', 'coll', 'data', 'other');


-- :name create-tables :!
CREATE SEQUENCE form_id_serial;
-- ;;
CREATE SEQUENCE file_id_serial;
-- ;;
CREATE SEQUENCE repository_id_serial;
-- ;;
CREATE TABLE repositories (repository_id INTEGER PRIMARY KEY DEFAULT nextval('repository_id_serial'),
	owner VARCHAR,
	name VARCHAR);
-- ;;
CREATE TABLE files (file_id INTEGER PRIMARY KEY DEFAULT nextval('file_id_serial'),
	file_name VARCHAR,
	repository_id INTEGER);
-- ;;
CREATE TABLE forms (form_id INTEGER PRIMARY KEY DEFAULT nextval('form_id_serial'),
	form_uuid UUID,
	parent_form_uuid UUID,
	root_form_uuid UUID,
	form_depth INTEGER,
	form_type form_type,
	form VARCHAR,
	resolved_symbol VARCHAR,
	clojure_type VARCHAR,
	meta VARCHAR,
	file_id INTEGER,
	file_line INTEGER,
	file_column INTEGER,
	FOREIGN KEY (file_id) REFERENCES files(file_id));
-- ;;


-- :name insert-files :!
INSERT INTO files (file_name)
VALUES :t*:vals;

-- :name insert-forms :!

INSERT INTO forms (form_uuid, parent_form_uuid, root_form_uuid, form_depth, file_id, form_type, form, resolved_symbol, meta, clojure_type, file_line, file_column)
SELECT form_values.form_uuid, form_values.parent_form_uuid, form_values.root_form_uuid, form_values.form_depth, files.file_id, form_type, form, resolved_symbol, meta, clojure_type, file_line, file_column
FROM (VALUES :t*:vals) form_values(form_uuid, parent_form_uuid, root_form_uuid, form_depth, file_name, form_type, form, resolved_symbol, meta, clojure_type, file_line, file_column)
	LEFT OUTER JOIN files ON (form_values.file_name = files.file_name) ;


-- :name insert-repositories-from-files :!

WITH files_and_repos AS (SELECT file_id,  file_name, regexp_extract(file_name, :pattern1 , 1) AS repo_owner, regexp_extract(file_name, '2025_10_17/.*?/(.*?)/', 1) AS repo_name
FROM files),
repos AS (SELECT repo_owner, repo_name FROM files_and_repos GROUP BY repo_owner, repo_name)
INSERT INTO repositories (owner, name)
SELECT *
FROM repos;


-- :name add-repo-ids :!
WITH files_and_repos AS (SELECT file_id,  file_name, regexp_extract(file_name, :pattern1, 1) AS repo_owner, regexp_extract(file_name, :pattern2, 1) AS repo_name
FROM files),
repos AS (SELECT ROW_NUMBER() OVER () AS row_id, repo_owner AS repo_owner, repo_name AS repo_name FROM files_and_repos GROUP BY repo_owner, repo_name),
joined AS (SELECT files_and_repos.*, repos.row_id
				FROM files_and_repos
				LEFT OUTER JOIN repos ON (repos.repo_owner = files_and_repos.repo_owner AND repos.repo_name = files_and_repos.repo_name))
UPDATE files
SET repository_id = row_id
FROM joined
WHERE joined.file_name = files.file_name;

