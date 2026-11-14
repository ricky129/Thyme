package com.ricky.thyme.remote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SshTargetTest {
    @Test
    void acceptsUsernameWithTailscaleIpOrDnsName() {
        assertTrue(SshTarget.isValid("myserver@100.73.45.88"));
        assertTrue(SshTarget.isValid("myserver@myserver.tail33f299.ts.net"));
        assertTrue(SshTarget.isValid("myserver@myserver.com"));
        assertTrue(SshTarget.isValid("myserver@home-server"));
        assertTrue(SshTarget.isValid("myserver@example.com."));
    }

    @Test
    void rejectsMissingUsernameOrMalformedComponents() {
        assertFalse(SshTarget.isValid("100.73.45.88"));
        assertFalse(SshTarget.isValid("@myserver.com"));
        assertFalse(SshTarget.isValid("myserver@"));
        assertFalse(SshTarget.isValid("myserver@@myserver.com"));
        assertFalse(SshTarget.isValid("-oProxyCommand=sh@myserver.com"));
        assertFalse(SshTarget.isValid("myserver@host.example;id"));
        assertFalse(SshTarget.isValid("myserver@-oProxyCommand=sh"));
        assertFalse(SshTarget.isValid("myserver@host name"));
        assertFalse(SshTarget.isValid("myserver@100.999.45.88"));
    }
}
