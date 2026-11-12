package com.ricky.thyme.remote;

public final class SshTarget {
    private SshTarget() {}

    public static boolean isValid(String value) {
        if (value == null || value.length() > 286) {
            return false;
        }
        int separator = value.indexOf('@');
        if (separator <= 0 || separator != value.lastIndexOf('@') || separator == value.length() - 1) {
            return false;
        }

        String username = value.substring(0, separator);
        String host = value.substring(separator + 1);
        return username.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,31}") && HostAddress.isValid(host);
    }
}
