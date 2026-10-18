package com.ricky.drivebay.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.ricky.drivebay.protocol.Close;
import com.ricky.drivebay.protocol.Data;
import com.ricky.drivebay.protocol.Message;
import com.ricky.drivebay.protocol.Open;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class Dispatcher {
    private final Map<String, Feature> features = new HashMap<>();
    private final Map<String, ActiveChannel> channels = new ConcurrentHashMap<>();
    private final Consumer<Message> out;   // deve essere thread-safe

    public Dispatcher(Collection<Feature> features, Consumer<Message> out) {
        for (Feature f : features) {
            if (f.isAvailable()) this.features.put(f.payload(), f);
        }
        this.out = out;
    }

    public void handle(Message message) {
        switch (message) {
            case Open o  -> open(o);
            case Close c -> closeFromClient(c);
            case Data _  -> { }   // servirà per i canali bidirezionali (terminale)
            default -> throw new IllegalStateException("Unexpected value: " + message);
        }
    }

    private void open(Open request) {
        Feature feature = features.get(request.payload());
        if (feature == null) {
            out.accept(new Close(request.channel(), "not-found"));
            return;
        }
        var channel = new ActiveChannel(request.channel());
        if (channels.putIfAbsent(channel.id(), channel) != null) {
            out.accept(new Close(request.channel(), "protocol-error"));
            return;
        }
        channel.worker = Thread.ofVirtual().name("channel-" + channel.id()).start(() -> {
            try {
                feature.open(channel, request);
                channel.close();
            } catch (InterruptedException e) {
                // il client ha chiuso il canale: non è un errore
            } catch (Exception e) {
                System.err.println("feature " + request.payload() + " failed: " + e);
                channel.close("internal-error");
            }
        });
    }

    private void closeFromClient(Close close) {
        ActiveChannel channel = channels.remove(close.channel());
        if (channel != null) channel.closedByPeer();
    }

    public void shutdown() {
        channels.values().forEach(ActiveChannel::closedByPeer);
        channels.clear();
    }

    private final class ActiveChannel implements Channel {
        private final String id;
        private final AtomicBoolean closed = new AtomicBoolean();
        volatile Thread worker;

        ActiveChannel(String id) { this.id = id; }

        public String id() { return id; }
        public boolean isClosed() { return closed.get(); }

        public void send(JsonNode body) {
            if (!closed.get()) out.accept(new Data(id, body));
        }

        public void close() { close(null); }

        public void close(String problem) {
            if (closed.compareAndSet(false, true)) {
                channels.remove(id, this);
                out.accept(new Close(id, problem));
            }
        }

        void closedByPeer() {
            if (closed.compareAndSet(false, true)) {
                Thread t = worker;
                if (t != null) t.interrupt();
            }
        }
    }
}