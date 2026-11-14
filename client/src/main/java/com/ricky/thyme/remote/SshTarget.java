package com.ricky.thyme.remote;

public final class SshTarget {
    private SshTarget() {}

    public static boolean isValid(String value) {
        if (value == null || value.length() > 286)
            return false;
        int separator = value.indexOf('@');
        if (separator <= 0 || separator != value.lastIndexOf('@') || separator == value.length() - 1)
            return false;

        String username = value.substring(0, separator);
        String host = value.substring(separator + 1);
        return username.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,31}") && isValidHost(host);
    }

    private static boolean isValidHost(String value) {
        if (value == null || value.isBlank() || value.length() > 253)
            return false;
        if (value.matches("[0-9.]+"))
            return isIpv4(value);
        String host = value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
        if (host.isEmpty())
            return false;
        for (String label : host.split("\\.", -1)) {
            if (label.isEmpty()
                    || label.length() > 63
                    || !label.matches("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"))
                return false;
        }
        return true;
    }

    private static boolean isIpv4(String value) {
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4)
            return false;
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !octet.matches("[0-9]+"))
                return false;
            try {
                if (Integer.parseInt(octet) > 255)
                    return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}
