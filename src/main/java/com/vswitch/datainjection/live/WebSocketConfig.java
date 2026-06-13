package com.vswitch.datainjection.live;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@ConditionalOnProperty(name = "live.updates.enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketConfig implements WebSocketConfigurer {

    private final TenantLiveWebSocketHandler handler;

    WebSocketConfig(TenantLiveWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/live").setAllowedOrigins("*");
    }
}
