--liquibase formatted sql

--changeset finflow:010-add-tenant-feature-settings

ALTER TABLE tenants ADD tickets_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenants ADD public_chat_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tenants ADD crawl_start_url VARCHAR(500);
ALTER TABLE tenants ADD knowledge_source VARCHAR(100) NOT NULL DEFAULT 'knowledge-base';
ALTER TABLE tenants ADD knowledge_document_type VARCHAR(100) NOT NULL DEFAULT 'business-documentation';
ALTER TABLE tenants ADD document_metadata TEXT;

UPDATE tenants
SET tickets_enabled = TRUE,
    knowledge_source = 'onboarding',
    knowledge_document_type = 'internal-documentation',
    document_metadata = '{"*":{"category":"other","department":"IT"},"company-overview.md":{"category":"company","department":"IT"},"new-employee-onboarding.md":{"category":"onboarding","department":"IT"},"gitlab-access.md":{"category":"access","department":"IT"},"vpn-and-wlan-access.md":{"category":"network","department":"IT"},"laptop-request.md":{"category":"hardware","department":"IT"},"equipment-request.md":{"category":"hardware","department":"IT"},"software-license-request.md":{"category":"software","department":"IT"}}'
WHERE slug = 'finflow';

UPDATE tenants
SET public_chat_enabled = TRUE,
    crawl_start_url = 'http://localhost:3000/',
    document_metadata = '{"*":{"category":"other"},"espresso-guide.md":{"category":"products"},"shipping-policy.md":{"category":"shipping"},"return-policy.md":{"category":"returns"}}'
WHERE slug = 'brewly';
