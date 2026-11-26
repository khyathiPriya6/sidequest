package com.sidequest.group;

final class GroupFailure extends RuntimeException {
    final int status;
    final String code;
    private GroupFailure(int status,String code,String message) {
        super(message); this.status=status; this.code=code;
    }
    static GroupFailure bad() { return new GroupFailure(400,"invalid_group_request","Invalid team or challenge request."); }
    static GroupFailure missing() { return new GroupFailure(404,"not_found","Team resource unavailable."); }
    static GroupFailure conflict() { return new GroupFailure(409,"group_conflict","Team state changed. Reload before retrying."); }
    static GroupFailure limited() { return new GroupFailure(429,"group_rate_limited","Team invitation limit reached."); }
}
