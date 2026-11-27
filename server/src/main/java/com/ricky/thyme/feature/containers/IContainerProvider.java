package com.ricky.thyme.feature.containers;

import java.io.IOException;
import java.util.List;

public interface IContainerProvider {

    List<ContainerSnapshot> list() throws IOException, InterruptedException;

    String logs(String id) throws IOException, InterruptedException;

    void start(String id) throws IOException, InterruptedException;

    void stop(String id) throws IOException, InterruptedException;

    void restart(String id) throws IOException, InterruptedException;
}
