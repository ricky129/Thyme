package com.ricky.thyme.remote;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostAddressTest {
    @Test
    void acceptsTailscaleAddressesAndDnsNames() {
        assertTrue(HostAddress.isValid("100.73.45.88"));
        assertTrue(HostAddress.isValid("myserver.tail33f299.ts.net"));
        assertTrue(HostAddress.isValid("myserver.com"));
        assertTrue(HostAddress.isValid("home-server"));
    }

    @Test
    void rejectsInvalidOrOptionLikeHosts() {
        assertFalse(HostAddress.isValid("100.999.45.88"));
        assertFalse(HostAddress.isValid("-oProxyCommand=sh"));
        assertFalse(HostAddress.isValid("host.example;id"));
        assertFalse(HostAddress.isValid("host name"));
        assertFalse(HostAddress.isValid(""));
    }
}
