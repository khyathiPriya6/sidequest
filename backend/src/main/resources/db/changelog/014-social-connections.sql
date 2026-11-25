--liquibase formatted sql
--changeset sidequest:014-social-connections
CREATE TABLE friend_connections (
  user_low_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  user_high_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  requester_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  state VARCHAR(12) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_low_id, user_high_id),
  CONSTRAINT friend_pair_order CHECK (user_low_id < user_high_id),
  CONSTRAINT friend_requester_is_member CHECK (requester_id IN (user_low_id, user_high_id)),
  CONSTRAINT friend_state_valid CHECK (state IN ('PENDING','ACCEPTED','REJECTED','CANCELLED','REMOVED')),
  CONSTRAINT friend_version_valid CHECK (version >= 0)
);
CREATE INDEX friend_low_state_idx ON friend_connections(user_low_id,state,updated_at);
CREATE INDEX friend_high_state_idx ON friend_connections(user_high_id,state,updated_at);
CREATE INDEX friend_requester_idx ON friend_connections(requester_id,state,requested_at);

CREATE TABLE social_blocks (
  blocker_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  blocked_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (blocker_id, blocked_id),
  CONSTRAINT social_no_self_block CHECK (blocker_id <> blocked_id)
);
CREATE INDEX social_blocked_idx ON social_blocks(blocked_id,blocker_id);

CREATE TABLE friend_request_events (
  id UUID PRIMARY KEY,
  actor_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  peer_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
  action VARCHAR(12) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT friend_event_action CHECK (action IN ('SEND','ACCEPT','REJECT','CANCEL','REMOVE','BLOCK','UNBLOCK'))
);
CREATE INDEX friend_request_events_rate_idx ON friend_request_events(actor_id,action,created_at DESC);
--rollback DROP TABLE friend_request_events;
--rollback DROP TABLE social_blocks;
--rollback DROP TABLE friend_connections;
