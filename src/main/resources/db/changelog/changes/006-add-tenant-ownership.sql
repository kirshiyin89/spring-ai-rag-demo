--liquibase formatted sql

--changeset finflow:006-add-tenant-ownership

ALTER TABLE tickets ADD COLUMN tenant_id BIGINT;

UPDATE tickets
SET tenant_id = (SELECT id FROM tenants WHERE slug = 'finflow');

ALTER TABLE tickets
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE tickets
    ADD CONSTRAINT fk_tickets_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

CREATE INDEX idx_tickets_tenant_id ON tickets (tenant_id);

ALTER TABLE ticket_status_change_requests ADD COLUMN tenant_id BIGINT;

UPDATE ticket_status_change_requests r
SET tenant_id = t.tenant_id
FROM tickets t
WHERE t.ticket_id = r.ticket_id;

ALTER TABLE ticket_status_change_requests
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE ticket_status_change_requests
    ADD CONSTRAINT fk_ticket_change_requests_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

CREATE INDEX idx_ticket_change_requests_tenant_id
    ON ticket_status_change_requests (tenant_id);

ALTER TABLE pending_issue_drafts ADD COLUMN tenant_id BIGINT;

UPDATE pending_issue_drafts
SET tenant_id = (SELECT id FROM tenants WHERE slug = 'finflow');

ALTER TABLE pending_issue_drafts
    ALTER COLUMN tenant_id SET NOT NULL;

ALTER TABLE pending_issue_drafts
    ADD CONSTRAINT fk_pending_issue_drafts_tenant
        FOREIGN KEY (tenant_id) REFERENCES tenants (id);

ALTER TABLE pending_issue_drafts
    DROP CONSTRAINT IF EXISTS uk_draft_user_conversation;

ALTER TABLE pending_issue_drafts
    ADD CONSTRAINT uk_draft_tenant_user_conversation
        UNIQUE (tenant_id, username, conversation_id);
