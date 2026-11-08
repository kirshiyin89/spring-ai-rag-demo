--liquibase formatted sql

--changeset yourname:004-create-pending-issue-drafts

CREATE TABLE pending_issue_drafts
(
    draft_id        UUID PRIMARY KEY,
    username        VARCHAR(255)             NOT NULL,
    conversation_id VARCHAR(255)             NOT NULL,
    summary         VARCHAR(500)             NOT NULL,
    description     TEXT                     NOT NULL,
    status          VARCHAR(20)              NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    version         BIGINT,

    CONSTRAINT uk_draft_user_conversation
        UNIQUE (username, conversation_id)
);