--
-- Copyright © 2016-2024 The Thingsboard Authors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- KV VERSIONING UPDATE START

CREATE SEQUENCE IF NOT EXISTS attribute_kv_version_seq cache 1;
CREATE SEQUENCE IF NOT EXISTS ts_kv_latest_version_seq cache 1;

ALTER TABLE attribute_kv ADD COLUMN IF NOT EXISTS version bigint default 0;
ALTER TABLE ts_kv_latest ADD COLUMN IF NOT EXISTS version bigint default 0;

-- KV VERSIONING UPDATE END

-- RELATION VERSIONING UPDATE START

CREATE SEQUENCE IF NOT EXISTS relation_version_seq cache 1;
ALTER TABLE relation ADD COLUMN IF NOT EXISTS version bigint default 0;

-- RELATION VERSIONING UPDATE END


-- ENTITIES VERSIONING UPDATE START

ALTER TABLE device ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE device_profile ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE device_credentials ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE asset ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE asset_profile ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE entity_view ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE tb_user ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE customer ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE edge ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE rule_chain ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE dashboard ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE widget_type ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE widgets_bundle ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;
ALTER TABLE tenant ADD COLUMN IF NOT EXISTS version BIGINT DEFAULT 1;

-- ENTITIES VERSIONING UPDATE END

-- OAUTH2 UPDATE START

ALTER TABLE IF EXISTS oauth2_mobile RENAME TO mobile_app;
ALTER TABLE IF EXISTS oauth2_domain RENAME TO domain;
ALTER TABLE IF EXISTS oauth2_registration RENAME TO oauth2_client;

ALTER TABLE domain ADD COLUMN IF NOT EXISTS oauth2_enabled boolean,
    ADD COLUMN IF NOT EXISTS edge_enabled boolean,
    ADD COLUMN IF NOT EXISTS tenant_id uuid DEFAULT '13814000-1dd2-11b2-8080-808080808080',
DROP COLUMN IF EXISTS domain_scheme;

-- delete duplicated domains
DELETE FROM domain d1 USING domain d2 WHERE d1.created_time < d2.created_time AND d1.domain_name = d2.domain_name;

ALTER TABLE mobile_app ADD COLUMN IF NOT EXISTS oauth2_enabled boolean,
    ADD COLUMN IF NOT EXISTS tenant_id uuid DEFAULT '13814000-1dd2-11b2-8080-808080808080';

-- delete duplicated apps
DELETE FROM mobile_app m1 USING mobile_app m2 WHERE m1.created_time < m2.created_time AND m1.pkg_name = m2.pkg_name;

ALTER TABLE oauth2_client ADD COLUMN IF NOT EXISTS tenant_id uuid DEFAULT '13814000-1dd2-11b2-8080-808080808080',
    ADD COLUMN IF NOT EXISTS title varchar(100);
UPDATE oauth2_client SET title = additional_info::jsonb->>'providerName' WHERE additional_info IS NOT NULL;

CREATE TABLE IF NOT EXISTS domain_oauth2_client (
                                                    domain_id uuid NOT NULL,
                                                    oauth2_client_id uuid NOT NULL,
                                                    CONSTRAINT fk_domain FOREIGN KEY (domain_id) REFERENCES domain(id) ON DELETE CASCADE,
    CONSTRAINT fk_oauth2_client FOREIGN KEY (oauth2_client_id) REFERENCES oauth2_client(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS mobile_app_oauth2_client (
                                                        mobile_app_id uuid NOT NULL,
                                                        oauth2_client_id uuid NOT NULL,
                                                        CONSTRAINT fk_domain FOREIGN KEY (mobile_app_id) REFERENCES mobile_app(id) ON DELETE CASCADE,
    CONSTRAINT fk_oauth2_client FOREIGN KEY (oauth2_client_id) REFERENCES oauth2_client(id) ON DELETE CASCADE
    );

-- migrate oauth2_params table
DO
$$
BEGIN
        IF EXISTS(SELECT 1 FROM information_schema.tables WHERE table_name = 'oauth2_params') THEN
UPDATE domain SET oauth2_enabled = p.enabled,
                  edge_enabled = p.edge_enabled
    FROM oauth2_params p WHERE p.id = domain.oauth2_params_id;

UPDATE mobile_app SET oauth2_enabled = p.enabled
    FROM oauth2_params p WHERE p.id = mobile_app.oauth2_params_id;

INSERT INTO domain_oauth2_client(domain_id, oauth2_client_id)
    (SELECT d.id, r.id FROM domain d LEFT JOIN oauth2_client r on d.oauth2_params_id = r.oauth2_params_id
     WHERE r.platforms IS NULL OR r.platforms IN ('','WEB'));

INSERT INTO mobile_app_oauth2_client(mobile_app_id, oauth2_client_id)
    (SELECT m.id, r.id FROM mobile_app m LEFT JOIN oauth2_client r on m.oauth2_params_id = r.oauth2_params_id
     WHERE r.platforms IS NULL OR r.platforms IN ('','ANDROID','IOS'));

ALTER TABLE mobile_app RENAME CONSTRAINT oauth2_mobile_pkey TO mobile_app_pkey;
ALTER TABLE domain RENAME CONSTRAINT oauth2_domain_pkey TO domain_pkey;
ALTER TABLE oauth2_client RENAME CONSTRAINT oauth2_registration_pkey TO oauth2_client_pkey;

ALTER TABLE domain DROP COLUMN oauth2_params_id;
ALTER TABLE mobile_app DROP COLUMN oauth2_params_id;
ALTER TABLE oauth2_client DROP COLUMN oauth2_params_id;

ALTER TABLE mobile_app ADD CONSTRAINT mobile_app_unq_key UNIQUE (pkg_name);
ALTER TABLE domain ADD CONSTRAINT domain_unq_key UNIQUE (domain_name);

DROP TABLE IF EXISTS oauth2_params;
END IF;
END
$$;

-- OAUTH2 UPDATE END
