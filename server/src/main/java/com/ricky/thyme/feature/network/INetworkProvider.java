package com.ricky.thyme.feature.network;

import java.io.IOException;

public interface INetworkProvider {
    NetworkSnapshot read() throws IOException;
}
