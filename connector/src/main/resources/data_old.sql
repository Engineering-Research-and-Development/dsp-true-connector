INSERT INTO properties (ID, CREATED_ON, APPLICATION, PROFILE, LABEL, PROP_KEY, VVALUE) VALUES 
	(1, '2023-10-25 14:23:37.032002+00', 'connector','dev','latest','testProperty','This is my test value');
INSERT INTO properties (ID, CREATED_ON, APPLICATION, PROFILE, LABEL, PROP_KEY, VVALUE) VALUES 
	(2, '2023-10-25 14:23:37.032002+00', 'connector','dev','latest','firstLastName','First Last name');

INSERT INTO USERS (id,enabled,email,first_name,last_name,password,role) VALUES
  ('59d54e6d-a3f3-4a03-a093-d276e3068eef',true,'milisav@mail.com','Veljko', 'Petrovic','glavudajemkrajinunedajem1','ROLE_USER'),
  ('cab7b27b-f810-457d-b900-368994f6a640',true,'petar@mail.com','Stevan', 'Mokranjac','$2a$10$wQgl7stAxkVI1oxaynYU2uj.1IxzQ/ETygs32RoveH.rkgAfXAk5q','ROLE_ADMIN');
  
INSERT INTO DATASET_ENTITY  (id,created_date,created_by,modified_date,updated_by,title,description,keywords,distribution_format,distribution_service) VALUES
  ('3f051304-28f4-4885-9ae3-14de89aa1aaa','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','Test dataset title','Description Dataset title example','abc,123', 'dspace:s3+push','5a9f7901-f978-47e6-93b4-d308e40bd4e8'),
  ('eef3e9d4-57d4-45ff-95ea-efc0f35b4be7','2023-10-25 14:27:02.897475+00','test','2023-10-25 14:23:37.032002+00','test','Test dataset title second one','Description Dataset title for second catalog example','abc,123', '','');   
  
INSERT INTO DATASET_TO_CATALOG_ENTITY  (id,created_date,created_by,modified_date,updated_by,catalog_id,dataset_id)VALUES
  ('131b5d0c-6484-4af9-805e-4bb5a9df4b02','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','5a9f7901-f978-47e6-93b4-d308e40bd4e8','3f051304-28f4-4885-9ae3-14de89aa1aaa');
  
INSERT INTO LITERAL_EXPRESSION_ENTITY  (id,created_date,created_by,modified_date,updated_by,expression) VALUES
  ('ab60ad80-3380-4b9c-b4a4-365c76544100','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','https://w3id.org/edc/v0.0.1/ns/inForceDate'),
  ('fdc45797-e957-4955-8baf-ab7fd66ac4d6','2023-10-25 14:27:02.897475+00','test','2023-10-25 14:23:37.032002+00','test','2023-09-08T14:01:51.043Z');   
  
INSERT INTO CONSTRAINT_ENTITY  (id,created_date,created_by,modified_date,updated_by,operator,left_expression_id,right_expression_id) VALUES
  ('fdc45797-e957-4955-8baf-ab7fd66ac4d5','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','1','ab60ad80-3380-4b9c-b4a4-365c76544100','fdc45797-e957-4955-8baf-ab7fd66ac4d6');
  
INSERT INTO ACTION_ENTITY (id,created_date,created_by,modified_date,updated_by,included_in,type,constraint_id )VALUES
  ('36faedce-a2aa-4b84-843c-216bd4f2dc04','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','included somewhere','USE','fdc45797-e957-4955-8baf-ab7fd66ac4d5');
  
INSERT INTO CATALOG_ENTITY  (id,created_date,created_by,modified_date,updated_by,title,description,keywords,publisher) VALUES
  ('5a9f7901-f978-47e6-93b4-d308e40bd4e8','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','Test catalog title','Description catalog title example','abc,123','catalog publisher'),
  ('db36b40c-465b-11ee-be56-0242ac120002','2023-10-25 14:27:02.897475+00','test','2023-10-25 14:23:37.032002+00','test','Test catalog title second one','Description title for second catalog example','','second catalog publisher');   
   
INSERT INTO DATA_SERVICE_ENTITY  (id,created_date,created_by,modified_date,updated_by,endpoint_url,terms) VALUES
  ('5a9f7901-f978-47e6-93b4-d308e40bd4e8','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','https://dataservice1.url','DataService term 1'),
  ('db36b40c-465b-11ee-be56-0242ac120002','2023-10-25 14:27:02.897475+00','test','2023-10-25 14:23:37.032002+00','test','https://dataservice2.url','DataService term 2');   

INSERT INTO DATA_SERVICE_TO_CATALOG_ENTITY  (id,created_date,created_by,modified_date,updated_by,catalog_id,data_service_id)VALUES
  ('0b660820-466c-11ee-be56-0242ac120002','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','5a9f7901-f978-47e6-93b4-d308e40bd4e8','d847eafe-4665-11ee-be56-0242ac120002');
  
INSERT INTO PERMISSION_ENTITY  (id,created_date,created_by,modified_date,updated_by,assignee,assigner,target,action_id)VALUES
  ('9bf53ac3-e98a-4576-a821-429ef10b066e','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','http://trueconnector/assignee','http://trueconnector/assigner','http://trueconnector/artifact/1','36faedce-a2aa-4b84-843c-216bd4f2dc04');
  
INSERT INTO POLICY_ENTITY  (id,created_date,created_by,modified_date,updated_by,assignee,assigner,inherits_from,target)VALUES
  ('803f5181-f6c9-4ca2-b9b6-5919547ac27a','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','http://trueconnector/assignee','http://trueconnector/assigner',NULL,'36faedce-a2aa-4b84-843c-216bd4f2dc04');
  
INSERT INTO CONTRACT_OFFER_ENTITY  (id,created_date,created_by,modified_date,updated_by,asset_id,policy_id)VALUES
  ('4dff15c2-6552-4d47-a301-900041b16997','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','Some Asset ID not sure what','803f5181-f6c9-4ca2-b9b6-5919547ac27a');  
  
INSERT INTO CONTRACT_OFFER_TO_CATALOG_ENTITY  (id,created_date,created_by,modified_date,updated_by,catalog_id,contract_offer_id)VALUES
  ('f70314dc-2a9f-4383-840c-f68113549f47','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','5a9f7901-f978-47e6-93b4-d308e40bd4e8','4dff15c2-6552-4d47-a301-900041b16997');
  
-- Contract Negotiation part
INSERT INTO CONTRACT_NEGOTIATION_ENTITY  (id, created_date, created_by, modified_date, updated_by, provider_pid, consumer_pid, state) VALUES
  ('02dc6c0e-e36c-4fd0-913d-cd9b3c9e23d6','2023-10-25 14:23:37.032002+00','test','2023-10-25 14:23:37.032002+00','test','5a9f7901-f978-47e6-93b4-d308e40bd4e8','4dff15c2-6552-4d47-a301-900041b16997', 'REQUESTED');
  
  