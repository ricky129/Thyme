package com.ricky.drivebay.protocol.codec;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ricky.drivebay.protocol.Message;

public final class MessageCodec {
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** Oggetto Java → una riga di JSON (senza a capo). */
    public String encode(Message message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalArgumentException("impossibile codificare " + message, e);
        }
    }

    /** Una riga di JSON → oggetto Java (Open, Data o Close). */
    public Message decode(String line) {
        try {
            return mapper.readValue(line, Message.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("messaggio non valido: " + line, e);
        }
    }
}