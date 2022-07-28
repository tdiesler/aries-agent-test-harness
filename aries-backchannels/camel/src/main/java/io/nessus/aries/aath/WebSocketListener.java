package io.nessus.aries.aath;

import java.util.Collections;

import org.hyperledger.aries.webhook.AriesWebSocketListener;
import org.hyperledger.aries.webhook.IEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Response;
import okhttp3.WebSocket;

public class WebSocketListener extends AriesWebSocketListener {
    
    static final Logger log = LoggerFactory.getLogger(WebSocketListener.class);
    
    public enum WebSocketState {
        NEW, OPEN, CLOSING, CLOSED
    }
    
    public WebSocketListener(String label, IEventHandler handler) {
        super(label, Collections.singletonList(handler), null);
    }

    private WebSocketState state = WebSocketState.NEW;
    
    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        log.info("WebSocket Open: {}", response);
        state = WebSocketState.OPEN;
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        log.info("WebSocket Closing: {} {}", code, reason);
        state = WebSocketState.CLOSING;
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        log.info("WebSocket Closed: {} {}", code, reason);
        state = WebSocketState.CLOSED;
    }

    public WebSocketState getState() {
        return state;
    }
}