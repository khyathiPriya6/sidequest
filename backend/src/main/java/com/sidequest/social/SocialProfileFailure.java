package com.sidequest.social;

final class SocialProfileFailure extends RuntimeException {
    final int status;
    final String code;

    private SocialProfileFailure(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    static SocialProfileFailure invalid() {
        return new SocialProfileFailure(400, "invalid_social_settings", "Invalid social profile settings.");
    }
    static SocialProfileFailure notFound() {
        return new SocialProfileFailure(404, "not_found", "Profile not found.");
    }
    static SocialProfileFailure conflict() {
        return new SocialProfileFailure(409, "version_conflict", "Social profile settings changed. Reload before retrying.");
    }
}
