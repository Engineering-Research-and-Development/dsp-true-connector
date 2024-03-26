INSERT INTO USERS (id, enabled, email, first_name, last_name, password, role)
VALUES ('59d54e6d-a3f3-4a03-a093-d276e3068eef', true, 'milisav@mail.com', 'Veljko', 'Petrovic',
        'glavudajemkrajinunedajem1', 'ROLE_USER'),
       ('cab7b27b-f810-457d-b900-368994f6a640', true, 'petar@mail.com', 'Stevan', 'Mokranjac',
        '$2a$10$wQgl7stAxkVI1oxaynYU2uj.1IxzQ/ETygs32RoveH.rkgAfXAk5q', 'ROLE_ADMIN');

-- CATALOG

INSERT INTO CAT_CATALOG (id, created_date, created_by, modified_date, updated_by,
                         CONFORMS_TO, CREATOR, IDENTIFIER, ISSUED, KEYWORD, MODIFIED, THEME, TITLE, HOMEPAGE,
                         PARTICIPANT_ID)
VALUES ('1dc45797-3333-4955-8baf-ab7fd66ac4d5', '2023-10-25 14:23:37.032002+00', 'test',
        '2023-10-25 14:23:37.032002+00', 'test',
        'conformsToSomething', 'Chuck Norris', 'Unique identifier for tests', 'yesterday', 'keyword1,keyword2,keyword3',
        'today', 'dark theme,light theme', 'Title for test', 'https://www.homepage.com/test',
        'urn:example:DataProviderA');

-- DATASET
INSERT INTO CAT_DATASET (id, created_date, created_by, modified_date, updated_by,
                         CONFORMS_TO, CREATOR, IDENTIFIER, ISSUED, KEYWORD, MODIFIED, THEME, TITLE, CATALOG_ID)
VALUES ('fdc45798-a222-4955-8baf-ab7fd66ac4d5', '2023-10-25 14:23:37.032002+00', 'test',
        '2023-10-25 14:23:37.032002+00', 'test',
        'conformsToSomething', 'Chuck Norris', 'Unique identifier for tests', 'yesterday', 'keyword1,keyword2,keyword3',
        'today', 'dark theme,light theme', 'Title for test', '1dc45797-3333-4955-8baf-ab7fd66ac4d5');

-- DISTRIBUTION - belongs to CATALOG AND DATASET
INSERT INTO CAT_DISTRIBUTION (id, created_date, created_by, modified_date, updated_by,
                              FORMAT, ISSUED, MODIFIED, TITLE, CATALOG_ID, DATASET_ID)
VALUES ('1dc45797-2222-4955-8baf-ab7fd66ac4d5', '2023-10-25 14:23:37.032002+00', 'test',
        '2023-10-25 14:23:37.032002+00', 'test',
        'dspace:s3+push', null, null, 'Flash lightning distribution',
        '1dc45797-3333-4955-8baf-ab7fd66ac4d5', 'fdc45798-a222-4955-8baf-ab7fd66ac4d5');

--- DATASERVICE (belongs to catalog and distribution)
INSERT INTO CAT_DATASERVICE (id, created_date, created_by, modified_date, updated_by,
                             ENDPOINT_DESCRIPTION, ENDPOINTURL, CATALOG_ID, DISTRIBUTION_ID)
VALUES ('1dc45797-4444-4955-8baf-ab7fd66ac4d5', '2023-10-25 14:23:37.032002+00', 'test',
        '2023-10-25 14:23:37.032002+00', 'test',
        'endpoint description', 'https://provider-a.com/connector', '1dc45797-3333-4955-8baf-ab7fd66ac4d5',
        '1dc45797-2222-4955-8baf-ab7fd66ac4d5');

INSERT INTO OFFER_OFFER (id, created_date, created_by, modified_date, updated_by,
                         DATASET_ID)
VALUES ('fdc45798-a123-4955-8baf-ab7fd66ac4d5', '2023-10-25 14:23:37.032002+00', 'test',
        '2023-10-25 14:23:37.032002+00', 'test',
        'fdc45798-a222-4955-8baf-ab7fd66ac4d5');

INSERT INTO OFFER_PERMISSION (id, created_date, created_by, modified_date, updated_by,
                              action, OFFER_ID)
VALUES ('fdc45798-e957-4955-8baf-ab7fd66ac4d5', '2023-10-25 14:23:37.032002+00', 'test',
        '2023-10-25 14:23:37.032002+00', 'test',
        'odrl:use', 'fdc45798-a123-4955-8baf-ab7fd66ac4d5');

INSERT INTO OFFER_CONSTRAINT (id, created_date, created_by, modified_date, updated_by,
                              LEFT_OPERAND, operator, RIGHT_OPERAND, PERMISSION_ID)
VALUES ('fdc45797-e957-4955-8baf-ab7fd66ac4d5', '2023-10-25 14:23:37.032002+00', 'test',
        '2023-10-25 14:23:37.032002+00', 'test',
        'odrl:count', 'odrl:EQ', '5', 'fdc45798-e957-4955-8baf-ab7fd66ac4d5');

INSERT INTO MULTILANGUAGE (id, created_date, created_by, modified_date, updated_by,
                           val, lang)
VALUES ('1dc45797-e957-4955-8baf-ab7fd66ac4d5', '2023-10-25 14:23:37.032002+00', 'test',
        '2023-10-25 14:23:37.032002+00', 'test',
        'Some text for multi language', 'en');
INSERT INTO CAT_CATALOG_DESCRIPTION (CATALOG_ID, DESCRIPTION_ID)
VALUES ('1dc45797-3333-4955-8baf-ab7fd66ac4d5', '1dc45797-e957-4955-8baf-ab7fd66ac4d5');


INSERT INTO MULTILANGUAGE (id, created_date, created_by, modified_date, updated_by,
                           val, lang)
VALUES ('1dc45797-1111-4955-8baf-ab7fd66ac4d5', '2023-10-25 14:23:37.032002+00', 'test',
        '2023-10-25 14:23:37.032002+00', 'test',
        'Dataset description', 'en');

-- INSERT INTO CAT_DATASET_DESCRIPTION (DATASET_ID, DESCRIPTION_ID) VALUES (
--	'fdc45798-a222-4955-8baf-ab7fd66ac4d5', '1dc45797-1111-4955-8baf-ab7fd66ac4d5');

-- INSERT NEGOTIATION DATA

INSERT INTO NEGOT_AGREEMENT (id, assigner, assignee, target, timestamp, created_date, modified_date, created_by,
                             updated_by)
VALUES ('fdc45798-a123-4955-8baf-ab7fd66ac4d1', 'AssignerA', 'AssigneeA', 'TargetA', CURRENT_TIMESTAMP,
        '2023-10-25 14:23:37.032002+00', '2023-10-25 14:23:37.032002+00', 'test', 'test');

INSERT INTO NEGOT_PERMISSION (id, assigner, assignee, target, action, agreement_id, created_date, modified_date,
                              created_by, updated_by)
VALUES ('fdc45798-e957-4955-8baf-ab7fd66ac4d2', 'AssignerA', 'AssigneeB', 'TargetB', 'READ',
        'fdc45798-a123-4955-8baf-ab7fd66ac4d1', '2023-10-25 14:23:37.032002+00', '2023-10-25 14:23:37.032002+00',
        'test', 'test');

INSERT INTO NEGOT_CONSTRAINT (id, left_operand, operator, right_operand, permission_id, created_date, modified_date,
                              created_by, updated_by)
VALUES ('fdc45797-e957-4955-8baf-ab7fd66ac4d3', 'COUNT', 'EQ', 'Value1', 'fdc45798-e957-4955-8baf-ab7fd66ac4d2',
        '2023-10-25 14:23:37.032002+00', '2023-10-25 14:23:37.032002+00', 'test', 'test');

INSERT INTO NEGOT_OFFER (id, target, assigner, assignee, created_date, modified_date, created_by, updated_by)
VALUES ('fdc45798-b234-4955-8baf-ab7fd66ac4d4', 'TargetOfferA', 'OfferAssignerA', 'OfferAssigneeA',
        '2023-10-25 14:23:37.032002+00', '2023-10-25 14:23:37.032002+00', 'test', 'test');

INSERT INTO NEGOT_PERMISSION (id, assigner, assignee, target, action, offer_id, created_date, modified_date, created_by,
                              updated_by)
VALUES ('fdc45798-f123-4955-8baf-ab7fd66ac4d5', 'OfferAssignerB', 'OfferAssigneeB', 'TargetOfferB', 'DELETE',
        'fdc45798-b234-4955-8baf-ab7fd66ac4d4', '2023-10-25 14:23:37.032002+00', '2023-10-25 14:23:37.032002+00',
        'test', 'test');
INSERT INTO contract_negotiation_entity (id, provider_pid, consumer_pid, state, created_date, modified_date, created_by,
                                         updated_by)
VALUES ('f47ac10b-58cc-4372-a567-0e02b2c3d479', 'provider1', 'consumer1', 'REQUESTED', '2023-01-01T00:00:00Z',
        '2023-01-01T00:00:00Z', 'admin', 'admin'),
       ('54e63ab2-9a4c-4b97-9d92-6388d3db8895', 'provider2', 'consumer2', 'OFFERED', '2023-02-01T00:00:00Z',
        '2023-02-01T00:00:00Z', 'user1', 'user1'),
       ('5f2b40b8-3b88-4eb8-9c5f-6c212f2a3e75', 'provider3', 'consumer3', 'ACCEPTED', '2023-03-01T00:00:00Z',
        '2023-03-01T00:00:00Z', 'user2', 'user2');
