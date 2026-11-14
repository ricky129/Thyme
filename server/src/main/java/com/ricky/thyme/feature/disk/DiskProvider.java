package com.ricky.thyme.feature.disk;

import java.io.IOException;

public interface DiskProvider {
    DiskSnapshot read() throws IOException;
}
