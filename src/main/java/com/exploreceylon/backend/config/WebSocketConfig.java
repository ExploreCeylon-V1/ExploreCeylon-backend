package com.exploreceylon.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.cors.allowed-origins:http://localhost:*,https://localhost:*,http://127.0.0.1:*,https://127.0.0.1:*,https://exploreceylon.me,https://www.exploreceylon.me,https://*.exploreceylon.me,http://3.109.16.23:5173,http://3.109.16.23:5174}")
    private List<String> allowedOrigins;

    private final ChatHandshakeInterceptor chatHandshakeInterceptor;
    private final ChatHandshakeHandler chatHandshakeHandler;
    private final ChatChannelInterceptor chatChannelInterceptor;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-chat")
                .setAllowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .addInterceptors(chatHandshakeInterceptor)
                .setHandshakeHandler(chatHandshakeHandler)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(chatChannelInterceptor);
    }
}
