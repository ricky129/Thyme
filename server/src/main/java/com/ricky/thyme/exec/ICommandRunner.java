package com.ricky.thyme.exec;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public interface ICommandRunner {
    Result run(List<String> command, Duration timeout) throws IOException, InterruptedException;

    record Result(int exitCode, String stdout, String stderr) {
        public boolean ok() {
            return exitCode == 0;
        }
    }
}
