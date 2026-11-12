package com.ricky.thyme.exec;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

public final class ProcessCommandRunner implements CommandRunner {
    private static final int MAX_OUTPUT_BYTES = 4 * 1024 * 1024;

    @Override
    public Result run(List<String> command, Duration timeout) throws IOException, InterruptedException {
        validate(command, timeout);

        var process = new ProcessBuilder(List.copyOf(command)).start();
        process.getOutputStream().close();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> stdout = executor.submit(() -> readCapped(process.getInputStream()));
            Future<String> stderr = executor.submit(() -> readCapped(process.getErrorStream()));

            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                destroyTree(process);
                throw new IOException("timeout dopo " + timeout + ": " + command.getFirst());
            }

            long outputWaitMillis = Math.max(1, timeout.toMillis());
            return new Result(
                    process.exitValue(),
                    getOutput(stdout, outputWaitMillis, process),
                    getOutput(stderr, outputWaitMillis, process));
        } catch (InterruptedException | IOException e) {
            destroyTree(process);
            throw e;
        } catch (ExecutionException | TimeoutException e) {
            destroyTree(process);
            throw new IOException("lettura output fallita: " + command.getFirst(), e);
        }
    }

    private static void validate(List<String> command, Duration timeout) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("comando vuoto");
        }
        if (command.stream().anyMatch(argument -> argument == null || argument.isEmpty())) {
            throw new IllegalArgumentException("argomento comando nullo o vuoto");
        }
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout deve essere positivo");
        }
        if (timeout.toMillis() == 0) {
            throw new IllegalArgumentException("timeout deve essere almeno un millisecondo");
        }
    }

    private static String getOutput(Future<String> output, long waitMillis, Process process)
            throws InterruptedException, ExecutionException, TimeoutException {
        try {
            return output.get(waitMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            destroyTree(process);
            throw e;
        }
    }

    private static String readCapped(InputStream input) throws IOException {
        byte[] captured = input.readNBytes(MAX_OUTPUT_BYTES);
        input.transferTo(OutputStream.nullOutputStream());
        return new String(captured, StandardCharsets.UTF_8);
    }

    private static void destroyTree(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }
}
