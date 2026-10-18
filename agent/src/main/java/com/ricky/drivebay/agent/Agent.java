package com.ricky.drivebay.agent;

import com.ricky.drivebay.agent.feature.memory.MemoryFeature;
import com.ricky.drivebay.agent.feature.memory.MemoryProvider;
import com.ricky.drivebay.protocol.Message;
import com.ricky.drivebay.protocol.codec.MessageCodec;
import com.ricky.drivebay.protocol.feature.memory.MemorySnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

public final class Agent {

    public static void main(String[] args) throws IOException {
        var codec = new MessageCodec();

        // 1. Teniamo da parte il vero stdout, riservato al protocollo...
        var protocolOut = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        // ...e facciamo in modo che qualsiasi System.out.println "per sbaglio" finisca su stderr.
        System.setOut(System.err);

        var in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        // 2. Come si manda un messaggio al client: codifica e scrivi una riga.
        Consumer<Message> send = message -> {
            synchronized (protocolOut) {            // più canali = più thread: una riga alla volta
                protocolOut.println(codec.encode(message));
                protocolOut.flush();
            }
        };

        // 3. Il centralino, con le funzionalità disponibili.
        var dispatcher = new Dispatcher(
                List.of(new MemoryFeature(new MemoryProvider() {
                    @Override
                    public MemorySnapshot read() {
                        return null;
                    }
                })),
                send);

        // 4. Ciclo principale: leggi una riga, decodificala, passala al Dispatcher.
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) continue;
            try {
                dispatcher.handle(codec.decode(line));
            } catch (Exception e) {
                System.err.println("messaggio non valido: " + e.getMessage());
            }
        }

        // 5. stdin chiuso = il client se n'è andato: chiudi tutti i canali.
        dispatcher.shutdown();
    }
}