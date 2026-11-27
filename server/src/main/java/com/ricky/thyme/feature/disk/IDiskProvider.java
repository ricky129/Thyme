package com.ricky.thyme.feature.disk;

import java.io.IOException;

public interface IDiskProvider {
    DiskSnapshot read() throws IOException;
}
