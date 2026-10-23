package com.example.pms.tenant.exception;

public class InvitationExpiredException extends RuntimeException {

    public InvitationExpiredException() {
        super("This invitation has expired");
    }
}