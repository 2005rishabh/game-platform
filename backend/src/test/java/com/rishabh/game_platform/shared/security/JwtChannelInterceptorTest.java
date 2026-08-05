package com.rishabh.game_platform.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

@ExtendWith(MockitoExtension.class)
class JwtChannelInterceptorTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Test
    void preSendSetsPrincipalForValidConnectMessage() {
        JwtChannelInterceptor interceptor = new JwtChannelInterceptor(jwtService, userDetailsService);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setNativeHeader("Authorization", "Bearer test-token");
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        when(jwtService.extractUsername("test-token")).thenReturn("alice");
        UserDetails userDetails = new User("alice", "password", List.of());
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtService.isTokenValid("test-token", userDetails)).thenReturn(true);

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("alice");
    }

    @Test
    void preSendRestoresPrincipalFromSessionForLaterSendMessages() {
        JwtChannelInterceptor interceptor = new JwtChannelInterceptor(jwtService, userDetailsService);

        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setNativeHeader("Authorization", "Bearer test-token");
        Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());

        when(jwtService.extractUsername("test-token")).thenReturn("alice");
        UserDetails userDetails = new User("alice", "password", List.of());
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtService.isTokenValid("test-token", userDetails)).thenReturn(true);

        Message<?> connectResult = interceptor.preSend(connectMessage, mock(MessageChannel.class));
        StompHeaderAccessor connectedAccessor = StompHeaderAccessor.wrap(connectResult);
        HashMap<String, Object> sessionAttributes = new HashMap<>(connectedAccessor.getSessionAttributes());
        connectedAccessor.setSessionAttributes(sessionAttributes);

        StompHeaderAccessor sendAccessor = StompHeaderAccessor.create(StompCommand.SEND);
        sendAccessor.setSessionAttributes(sessionAttributes);
        Message<byte[]> sendMessage = MessageBuilder.createMessage(new byte[0], sendAccessor.getMessageHeaders());

        Message<?> sendResult = interceptor.preSend(sendMessage, mock(MessageChannel.class));
        StompHeaderAccessor sendResultAccessor = StompHeaderAccessor.wrap(sendResult);

        assertThat(sendResultAccessor.getUser()).isNotNull();
        assertThat(sendResultAccessor.getUser().getName()).isEqualTo("alice");
    }
}
