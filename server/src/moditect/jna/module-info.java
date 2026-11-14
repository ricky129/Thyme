module com.sun.jna {
    requires java.logging;
    requires transitive java.desktop;

    exports com.sun.jna to com.github.oshi, com.sun.jna.platform;
    exports com.sun.jna.internal to com.github.oshi, com.sun.jna.platform;
    exports com.sun.jna.ptr to com.github.oshi, com.sun.jna.platform;
    exports com.sun.jna.win32 to com.github.oshi, com.sun.jna.platform;
}
