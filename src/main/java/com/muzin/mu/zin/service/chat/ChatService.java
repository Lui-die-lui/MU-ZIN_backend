package com.muzin.mu.zin.service.chat;

import com.muzin.mu.zin.common.TimeDefaults;
import com.muzin.mu.zin.dto.chat.ChatMessageResponse;
import com.muzin.mu.zin.dto.chat.ChatRoomResponse;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.chat.ChatMessage;
import com.muzin.mu.zin.entity.chat.ChatRoom;
import com.muzin.mu.zin.entity.reservation.LessonReservation;
import com.muzin.mu.zin.event.ChatMessageSentEvent;
import com.muzin.mu.zin.event.ChatReadEvent;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.repository.chat.ChatMessageRepository;
import com.muzin.mu.zin.repository.chat.ChatRoomRepository;
import com.muzin.mu.zin.repository.lesson.LessonReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 수강생 <-> 아티스트 1:1 채팅
 *  - 방: 수강생 1명 + 아티스트 1명. 아티스트가 예약을 "수락"하거나 문의를 "채팅으로 넘길" 때 생김/다시 열림
 *    (수강생의 요청·문의만으로는 열리지 않음 -> 여는 결정은 항상 아티스트)
 *  - 대화 가능 = 아무도 나가지 않음 AND
 *      ( 진행 중인 확정 예약(확정·완료 확인 중) 있음
 *        OR 마지막 완료 레슨이 끝난 지 14일 이내
 *        OR 아티스트가 문의를 채팅으로 넘긴 지 14일 이내 )
 *    그 외에는 읽기 전용 (대화 내용은 남음). 예약 기록으로 매번 계산 -> 스케줄러 없음
 *  - 나가기: 나간 사람 화면에서 그때까지의 메시지가 지워짐(복원 불가), 상대에게는 "나갔어요" + 읽기 전용
 *           둘 다 나가면 방과 메시지를 DB 에서 삭제
 *  - 참여자만 접근 (남의 방·내가 나간 방은 404), 정지된 계정은 JWT 필터/STOMP 연결에서 차단
 *  - 보내기 제한: 10초에 20개 (서버 메모리)
 *  - 메시지 수정: 보낸 사람만, 보낸 지 24시간 안 ("수정됨") / 삭제: 보낸 사람만, 언제든 ("삭제된 메시지예요", 내용은 DB 에서도 지움)
 *    수정·삭제도 실시간으로 두 사람에게 전달 (같은 /user/queue/chat, 같은 id 로 덮어씀)
 *  - 나가지 않은 방의 보관 기간은 아직 정하지 않음 (나중에)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    public static final int WRITABLE_DAYS = 14;
    public static final int EDITABLE_HOURS = 24;
    private static final int PAGE_SIZE = 30;
    private static final int RATE_LIMIT = 20;
    private static final long RATE_WINDOW_MS = 10_000;

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final LessonReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher publisher;

    private final Map<Long, Deque<Long>> sendLog = new ConcurrentHashMap<>();

    // 예약 수락 시: 방이 없으면 만들고, 있으면 다시 엶 (나갔던 사람에게 예전 메시지는 계속 안 보임)
    @Transactional
    public ChatRoom ensureRoom(User student, ArtistProfile artistProfile) {
        ChatRoom room = chatRoomRepository.findByStudent_UserIdAndArtistProfile_ArtistProfileId(student.getUserId(), artistProfile.getArtistProfileId())
                .orElseGet(() -> chatRoomRepository.save(ChatRoom.builder().student(student).artistProfile(artistProfile).build()));
        room.reopen();
        return room;
    }

    // 아티스트가 문의를 채팅으로 넘김: 방을 열고 14일 동안 대화 가능
    @Transactional
    public ChatRoom openByArtist(User student, ArtistProfile artistProfile) {
        ChatRoom room = ensureRoom(student, artistProfile);
        room.openByArtist(TimeDefaults.nowKst().plusDays(WRITABLE_DAYS));
        return room;
    }

    public List<ChatRoomResponse> getMyRooms(Long userId) {
        return chatRoomRepository.findMyRooms(userId).stream()
                .filter(r -> !r.hasLeft(userId))
                .map(r -> toResponse(r, userId, false))
                .toList();
    }

    public ChatRoomResponse getRoom(Long userId, Long roomId) {
        return toResponse(findMyRoom(userId, roomId), userId, true);
    }

    // 예약 상세 -> 채팅하기
    public Long findRoomIdForReservation(Long userId, Long reservationId) {
        LessonReservation r = reservationRepository.findById(reservationId)
                .orElseThrow(() -> ApiException.notFound("예약을 찾을 수 없어요."));
        ArtistProfile ap = r.getLesson().getArtistProfile();
        boolean participant = r.getUser().getUserId().equals(userId) || ap.getUser().getUserId().equals(userId);
        if (!participant) throw ApiException.notFound("예약을 찾을 수 없어요.");
        ChatRoom room = chatRoomRepository.findByStudent_UserIdAndArtistProfile_ArtistProfileId(r.getUser().getUserId(), ap.getArtistProfileId())
                .orElseThrow(() -> ApiException.notFound("아직 채팅방이 없어요. 아티스트가 예약을 수락하면 대화할 수 있어요."));
        if (room.hasLeft(userId)) {
            throw ApiException.notFound("나간 대화방이에요. 아티스트가 새 레슨을 수락하면 다시 대화할 수 있어요.");
        }
        return room.getChatRoomId();
    }

    public List<ChatMessageResponse> getMessages(Long userId, Long roomId, Long beforeId) {
        ChatRoom room = findMyRoom(userId, roomId);
        List<ChatMessage> page = new ArrayList<>(chatMessageRepository.findPage(roomId, beforeId, room.clearedIdOf(userId), PageRequest.of(0, PAGE_SIZE)));
        Collections.reverse(page); // 최신부터 가져온 것을 시간순으로
        return page.stream().map(this::toMessage).toList();
    }

    public long getUnreadTotal(Long userId) {
        return chatRoomRepository.findMyRooms(userId).stream()
                .filter(r -> !r.hasLeft(userId))
                .mapToLong(r -> unreadOf(r, userId))
                .sum();
    }

    @Transactional
    public ChatMessageResponse send(Long userId, Long roomId, String rawContent) {
        ChatRoom room = findMyRoom(userId, roomId);
        String content = rawContent == null ? "" : rawContent.strip();
        if (content.isEmpty()) throw ApiException.badRequest("메시지를 입력해주세요.");
        if (content.length() > 1000) throw ApiException.badRequest("메시지는 1000자까지 보낼 수 있어요.");

        if (room.counterpartLeft(userId)) {
            throw ApiException.conflict("CHAT_COUNTERPART_LEFT", "상대가 대화방을 나가서 메시지를 보낼 수 없어요.");
        }
        if (!isWritable(room)) {
            throw ApiException.conflict("CHAT_READ_ONLY", "지금은 읽기 전용이에요. 아티스트가 새 레슨을 수락하면 다시 대화할 수 있어요.");
        }
        checkRate(userId);
        return post(room, userId, content);
    }

    // 메시지 저장 + 실시간 전달 (문의를 채팅으로 넘길 때 문의·답변을 첫 메시지로 옮기는 데도 사용)
    @Transactional
    public ChatMessageResponse post(ChatRoom room, Long senderUserId, String content) {
        User sender = userRepository.findById(senderUserId).orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없어요."));
        ChatMessage saved = chatMessageRepository.saveAndFlush(ChatMessage.builder().chatRoom(room).sender(sender).content(content).build());
        room.onMessage(saved);
        room.markRead(senderUserId, saved.getChatMessageId()); // 내가 보낸 건 읽은 것으로

        ChatMessageResponse resp = toMessage(saved);
        publisher.publishEvent(new ChatMessageSentEvent(List.of(room.getStudent().getUserId(), room.getArtistProfile().getUser().getUserId()), resp, true));
        return resp;
    }

    @Transactional
    public ChatMessageResponse editMessage(Long userId, Long roomId, Long messageId, String rawContent) {
        ChatMessage m = findMyMessage(userId, roomId, messageId);
        String content = rawContent == null ? "" : rawContent.strip();
        if (content.isEmpty()) throw ApiException.badRequest("메시지를 입력해주세요.");
        if (content.length() > 1000) throw ApiException.badRequest("메시지는 1000자까지 보낼 수 있어요.");
        if (m.getCreateDt().plusHours(EDITABLE_HOURS).isBefore(TimeDefaults.nowKst())) {
            throw ApiException.conflict("CHAT_EDIT_EXPIRED", "보낸 지 " + EDITABLE_HOURS + "시간이 지난 메시지는 수정할 수 없어요.");
        }
        m.edit(content, TimeDefaults.nowKst());
        return broadcast(m);
    }

    @Transactional
    public ChatMessageResponse deleteMessage(Long userId, Long roomId, Long messageId) {
        ChatMessage m = findMyMessage(userId, roomId, messageId);
        m.delete(TimeDefaults.nowKst());
        return broadcast(m);
    }

    // 내가 보낸, 아직 지우지 않은 메시지 (남의 메시지·다른 방 메시지는 404)
    private ChatMessage findMyMessage(Long userId, Long roomId, Long messageId) {
        findMyRoom(userId, roomId);
        ChatMessage m = chatMessageRepository.findById(messageId)
                .filter(x -> x.getChatRoom().getChatRoomId().equals(roomId) && x.getSender().getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("내가 보낸 메시지만 수정·삭제할 수 있어요."));
        if (m.isDeleted()) throw ApiException.conflict("CHAT_MESSAGE_DELETED", "이미 삭제된 메시지예요.");
        return m;
    }

    private ChatMessageResponse broadcast(ChatMessage m) {
        chatMessageRepository.flush();
        ChatRoom room = m.getChatRoom();
        ChatMessageResponse resp = toMessage(m);
        publisher.publishEvent(new ChatMessageSentEvent(List.of(room.getStudent().getUserId(), room.getArtistProfile().getUser().getUserId()), resp, false));
        return resp;
    }

    @Transactional
    public void markRead(Long userId, Long roomId, Long lastMessageId) {
        ChatRoom room = findMyRoom(userId, roomId);
        Long last = room.getLastMessageId();
        Long target;
        if (last == null) target = null;
        else if (lastMessageId == null) target = last;
        else target = Math.min(lastMessageId, last); // 아직 없는 메시지까지 읽음 처리하지 않음
        if (target == null) return;
        Long before = room.lastReadIdOf(userId);
        room.markRead(userId, target);
        Long after = room.lastReadIdOf(userId);
        // 실제로 더 읽었을 때만 상대에게 알림 (상대 화면 "읽음" 표시를 바로 갱신)
        if (after != null && !after.equals(before)) {
            publisher.publishEvent(new ChatReadEvent(room.counterpartUserId(userId), roomId, after));
        }
    }

    // 나가기: 내 화면에서 메시지가 지워지고 복원되지 않음. 둘 다 나가면 방·메시지 삭제
    @Transactional
    public void leave(Long userId, Long roomId) {
        ChatRoom room = findMyRoom(userId, roomId);
        room.leave(userId);
        if (room.bothLeft()) {
            chatMessageRepository.deleteAllInRoom(room.getChatRoomId());
            chatRoomRepository.delete(room); // 문의의 chat_room_id 는 DB 가 null 로 바꿈
        }
    }

    // ---------- 규칙 ----------

    public boolean isWritable(ChatRoom room) {
        if (room.anyoneLeft()) return false;
        if (reservationRepository.existsActiveBetween(room.getStudent().getUserId(), room.getArtistProfile().getArtistProfileId())) return true;
        LocalDateTime until = writableUntil(room);
        return until != null && !until.isBefore(TimeDefaults.nowKst());
    }

    // 진행 중 예약이 없을 때 대화 가능한 마감: 마지막 완료 레슨 + 14일, 아티스트가 연 기간 중 늦은 쪽
    private LocalDateTime writableUntil(ChatRoom room) {
        LocalDateTime lastEnd = reservationRepository.findLastCompletedEnd(room.getStudent().getUserId(), room.getArtistProfile().getArtistProfileId());
        LocalDateTime lessonUntil = lastEnd == null ? null : lastEnd.plusDays(WRITABLE_DAYS);
        LocalDateTime artistUntil = room.getArtistOpenUntil();
        if (lessonUntil == null) return artistUntil;
        if (artistUntil == null) return lessonUntil;
        return lessonUntil.isAfter(artistUntil) ? lessonUntil : artistUntil;
    }

    private ChatRoom findMyRoom(Long userId, Long roomId) {
        ChatRoom room = chatRoomRepository.findWithParticipants(roomId)
                .orElseThrow(() -> ApiException.notFound("채팅방을 찾을 수 없어요."));
        // 남의 방·내가 나간 방은 있는지도 알려주지 않음
        if (!room.isParticipant(userId) || room.hasLeft(userId)) throw ApiException.notFound("채팅방을 찾을 수 없어요.");
        return room;
    }

    private long unreadOf(ChatRoom r, Long userId) {
        // 읽은 위치와 나가기 전에 지운 위치 중 뒤쪽부터 셈 (삼항식에 Long/long 을 섞으면 null 언박싱 NPE 라 if 로)
        Long read = r.lastReadIdOf(userId);
        Long cleared = r.clearedIdOf(userId);
        Long from;
        if (read == null) from = cleared;
        else if (cleared == null) from = read;
        else from = Math.max(read, cleared);
        return chatMessageRepository.countUnread(r.getChatRoomId(), userId, from);
    }

    private void checkRate(Long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> log = sendLog.computeIfAbsent(userId, k -> new ArrayDeque<>());
        synchronized (log) {
            while (!log.isEmpty() && now - log.peekFirst() > RATE_WINDOW_MS) log.pollFirst();
            if (log.size() >= RATE_LIMIT) {
                throw ApiException.tooManyRequests("CHAT_TOO_FAST", "메시지를 너무 빨리 보내고 있어요. 잠시 후 다시 보내주세요.", RATE_WINDOW_MS / 1000);
            }
            log.addLast(now);
        }
    }

    private ChatRoomResponse toResponse(ChatRoom r, Long me, boolean detail) {
        boolean meStudent = r.isStudent(me);
        User counterpart = meStudent ? r.getArtistProfile().getUser() : r.getStudent();
        boolean active = reservationRepository.existsActiveBetween(r.getStudent().getUserId(), r.getArtistProfile().getArtistProfileId());
        LocalDateTime until = active ? null : writableUntil(r);
        boolean writable = !r.anyoneLeft() && (active || (until != null && !until.isBefore(TimeDefaults.nowKst())));

        // 마지막 메시지 미리보기 (내가 지운 범위면 숨김)
        String lastMessage = null;
        Long cleared = r.clearedIdOf(me);
        if (r.getLastMessageId() != null && (cleared == null || r.getLastMessageId() > cleared)) {
            lastMessage = chatMessageRepository.findById(r.getLastMessageId())
                    .map(m -> m.isDeleted() ? "삭제된 메시지예요" : m.getContent()).orElse(null);
        }
        List<ChatRoomResponse.ReservationSummary> reservations = detail
                ? reservationRepository.findRecentBetween(r.getStudent().getUserId(), r.getArtistProfile().getArtistProfileId(), PageRequest.of(0, 3))
                    .stream()
                    .map(x -> new ChatRoomResponse.ReservationSummary(x.getReservationId(), x.getLesson().getTitle(),
                            x.getTimeSlot().getStartDt(), x.getTimeSlot().getEndDt(), x.getStatus().name()))
                    .toList()
                : List.of();

        return new ChatRoomResponse(
                r.getChatRoomId(),
                meStudent ? "STUDENT" : "ARTIST",
                counterpart.getUserId(),
                counterpart.getUsername(),
                counterpart.getProfileImgUrl(),
                meStudent ? r.getArtistProfile().getArtistProfileId() : null,
                lastMessage,
                lastMessage == null ? null : r.getLastMessageDt(),
                unreadOf(r, me),
                writable,
                r.counterpartLeft(me),
                until,
                r.lastReadIdOf(counterpart.getUserId()),
                reservations
        );
    }

    private ChatMessageResponse toMessage(ChatMessage m) {
        return new ChatMessageResponse(m.getChatMessageId(), m.getChatRoom().getChatRoomId(), m.getSender().getUserId(),
                m.isDeleted() ? "" : m.getContent(), m.getCreateDt(), m.getEditedDt(), m.isDeleted());
    }
}
