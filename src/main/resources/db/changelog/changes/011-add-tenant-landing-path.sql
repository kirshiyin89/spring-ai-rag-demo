--liquibase formatted sql

--changeset finflow:011-add-tenant-landing-path

ALTER TABLE tenants ADD landing_path VARCHAR(200) NOT NULL DEFAULT '/';

UPDATE tenants
SET landing_path = '/brewly.html'
WHERE slug = 'brewly';
