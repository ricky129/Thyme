package com.ricky.drivebay.agent;

import com.fasterxml.jackson.databind.JsonNode;

public interface Channel {
    String id();
    boolean isClosed();            // true se il client ha chiuso o l'abbiamo chiuso noi
    void send(JsonNode body);      // manda un messaggio Data
    void close();                  // chiusura normale
    void close(String problem);    // chiusura con errore (es. "not-supported")
}