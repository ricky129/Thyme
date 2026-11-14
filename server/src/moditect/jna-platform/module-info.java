module com.sun.jna.platform {
    requires java.logging;
    requires transitive com.sun.jna;
    requires transitive java.datatransfer;
    requires transitive java.desktop;

    exports com.sun.jna.platform to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.bsd to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.dnd to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.linux to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.mac to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.unix to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.unix.aix to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.unix.solaris to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.win32 to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.win32.COM to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.win32.COM.tlb to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.win32.COM.tlb.imp to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.win32.COM.util to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.win32.COM.util.annotation to com.github.oshi, com.sun.jna;
    exports com.sun.jna.platform.wince to com.github.oshi, com.sun.jna;
}
