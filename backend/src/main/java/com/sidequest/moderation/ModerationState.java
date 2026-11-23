package com.sidequest.moderation;

/** Review clearance is separate from a Quest's DRAFT/PUBLISHED/ARCHIVED lifecycle. */
public enum ModerationState {
    UNREVIEWED,
    PENDING_REVIEW,
    APPROVED,
    REJECTED,
    UNDER_REVIEW,
    HIDDEN
}
