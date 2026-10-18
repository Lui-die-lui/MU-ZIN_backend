package com.muzin.mu.zin.security.websocket;

import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.security.jwt.JwtUtils;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;

// WebSocket(STOMP) 인증
// - CONNECT: Authorization: Bearer <JWT> 확인 (HTTP 요청과 같은 토큰). 없는/틀린/만료 토큰, 정지 계정은 연결 거부
// - SUBSCRIBE: 내 사용자 목적지(/user/queue/...)만 허용 -> 남의 방 메시지를 구독할 수 없음
// - SEND: 사용하지 않음 (메시지 보내기는 REST)
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            accessor.setUser(authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (command == StompCommand.SUBSCRIBE) {
            String dest = accessor.getDestination();
            if (accessor.getUser() == null || dest == null || !dest.startsWith("/user/queue/")) {
                throw new MessagingException("구독할 수 없는 주소예요.");
            }
        } else if (command == StompCommand.SEND) {
            throw new MessagingException("메시지는 REST 로 보내주세요.");
        }
        return message;
    }

    private Principal authenticate(String header) {
        if (header == null || !header.startsWith("Bearer ")) throw new MessagingException("로그인이 필요해요.");
        try {
            Long userId = Long.parseLong(jwtUtils.getClaims(header.substring(7)).getSubject());
            User user = userRepository.findById(userId).orElseThrow(() -> new MessagingException("로그인이 필요해요."));
            if (user.isSuspended()) throw new MessagingException("이용이 정지된 계정이에요.");
            return new UsernamePasswordAuthenticationToken(String.valueOf(userId), null, List.of());
        } catch (JwtException | IllegalArgumentException e) {
            throw new MessagingException("로그인이 만료됐어요. 다시 로그인해주세요.");
        }
    }
}
