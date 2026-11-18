package com.sidequest.identity.api;

public class AuthFailure extends RuntimeException {
    final int status;
    final String code;
    AuthFailure(int status, String code, String message) { super(message); this.status = status; this.code = code; }
}
