package com.ricky.thyme.feature.network;

import java.io.IOException;

public interface NetworkProvider {
    NetworkSnapshot read() throws IOException;
}
