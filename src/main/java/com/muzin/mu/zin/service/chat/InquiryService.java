package com.muzin.mu.zin.service.chat;

import com.muzin.mu.zin.dto.chat.InquiryResponse;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.entity.User;
import com.muzin.mu.zin.entity.chat.ChatRoom;
import com.muzin.mu.zin.entity.chat.LessonInquiry;
import com.muzin.mu.zin.entity.notification.NotificationRefType;
import com.muzin.mu.zin.entity.notification.NotificationType;
import com.muzin.mu.zin.event.NotificationEvent;
import com.muzin.mu.zin.exception.ApiException;
import com.muzin.mu.zin.repository.UserRepository;
import com.muzin.mu.zin.repository.artist.ArtistProfileRepository;
import com.muzin.mu.zin.repository.chat.LessonInquiryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/*
 * 레슨 문의 (레슨·예약 가능한 시간이 없어도 아티스트에게 질문)
 *  - 승인된 아티스트에게만, 내 아티스트 프로필에는 불가
 *  - 같은 아티스트에게 답변을 기다리는 문의는 1개까지 (스팸 방지)
 *  - 아티스트: 답변 1개 (OPEN -> REPLIED)  또는  채팅으로 넘기기 (OPEN/REPLIED -> CHAT)
 *    채팅으로 넘기면 문의(와 답변)가 첫 메시지가 되고 14일 동안 대화 가능 (ChatService.openByArtist)
 *  - 알림: 새 문의 -> 아티스트 / 답변·채팅 전환 -> 문의한 사람
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InquiryService {

    private final LessonInquiryRepository inquiryRepository;
    private final ArtistProfileRepository artistProfileRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final ApplicationEventPublisher publisher;

    @Transactional
    public InquiryResponse create(Long userId, Long artistProfileId, String rawContent) {
        ArtistProfile artist = artistProfileRepository.findById(artistProfileId)
                .orElseThrow(() -> ApiException.notFound("아티스트를 찾을 수 없어요."));
        if (artist.getUser().getArtistStatus() != ArtistStatus.APPROVED) throw ApiException.notFound("아티스트를 찾을 수 없어요.");
        if (Objects.equals(artist.getUser().getUserId(), userId)) throw ApiException.badRequest("내 프로필에는 문의할 수 없어요.");
        String content = normalize(rawContent, "문의 내용을 입력해주세요.");

        if (inquiryRepository.existsByUser_UserIdAndArtistProfile_ArtistProfileIdAndStatus(userId, artistProfileId, LessonInquiry.Status.OPEN)) {
            throw ApiException.conflict("INQUIRY_PENDING", "이 아티스트에게 보낸 문의가 아직 답변을 기다리고 있어요. 답변을 받은 뒤 다시 문의할 수 있어요.");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("사용자를 찾을 수 없어요."));
        LessonInquiry saved = inquiryRepository.save(LessonInquiry.builder().user(user).artistProfile(artist).content(content).build());

        publisher.publishEvent(new NotificationEvent(artist.getUser().getUserId(), NotificationType.INQUIRY_CREATED,
                "새 레슨 문의", limit(user.getDisplayName() + "님이 레슨을 문의했어요."), NotificationRefType.INQUIRY, saved.getInquiryId()));
        return toResponse(saved, userId);
    }

    public List<InquiryResponse> getSent(Long userId) {
        return inquiryRepository.findSent(userId).stream().map(i -> toResponse(i, userId)).toList();
    }

    public List<InquiryResponse> getReceived(Long userId) {
        return inquiryRepository.findReceived(userId).stream().map(i -> toResponse(i, userId)).toList();
    }

    public long countOpenReceived(Long userId) {
        return inquiryRepository.countByArtistProfile_User_UserIdAndStatus(userId, LessonInquiry.Status.OPEN);
    }

    // 답변하기 (한 번)
    @Transactional
    public InquiryResponse reply(Long artistUserId, Long inquiryId, String rawContent) {
        LessonInquiry inquiry = findForArtist(artistUserId, inquiryId);
        if (inquiry.getStatus() != LessonInquiry.Status.OPEN) {
            throw ApiException.conflict("INQUIRY_ALREADY_ANSWERED", "이미 답변했거나 채팅으로 넘긴 문의예요.");
        }
        inquiry.reply(normalize(rawContent, "답변 내용을 입력해주세요."));
        publisher.publishEvent(new NotificationEvent(inquiry.getUser().getUserId(), NotificationType.INQUIRY_REPLIED,
                "문의 답변", limit(inquiry.getArtistProfile().getUser().getDisplayName() + "님이 문의에 답변했어요."), NotificationRefType.INQUIRY, inquiry.getInquiryId()));
        return toResponse(inquiry, artistUserId);
    }

    // 채팅으로 넘기기: 방을 열고(14일) 문의·답변을 첫 메시지로
    @Transactional
    public InquiryResponse toChat(Long artistUserId, Long inquiryId) {
        LessonInquiry inquiry = findForArtist(artistUserId, inquiryId);
        if (inquiry.getStatus() == LessonInquiry.Status.CHAT) {
            throw ApiException.conflict("INQUIRY_ALREADY_CHAT", "이미 채팅으로 넘긴 문의예요.");
        }
        ChatRoom room = chatService.openByArtist(inquiry.getUser(), inquiry.getArtistProfile());
        chatService.post(room, inquiry.getUser().getUserId(), "[레슨 문의] " + inquiry.getContent());
        if (inquiry.getReplyContent() != null) {
            chatService.post(room, artistUserId, inquiry.getReplyContent());
        }
        inquiry.toChat(room.getChatRoomId());
        publisher.publishEvent(new NotificationEvent(inquiry.getUser().getUserId(), NotificationType.INQUIRY_TO_CHAT,
                "문의가 채팅으로 이어졌어요", limit(inquiry.getArtistProfile().getUser().getDisplayName() + "님과 채팅으로 이야기할 수 있어요."),
                NotificationRefType.CHAT_ROOM, room.getChatRoomId()));
        return toResponse(inquiry, artistUserId);
    }

    private LessonInquiry findForArtist(Long artistUserId, Long inquiryId) {
        LessonInquiry inquiry = inquiryRepository.findById(inquiryId).orElseThrow(() -> ApiException.notFound("문의를 찾을 수 없어요."));
        if (!Objects.equals(inquiry.getArtistProfile().getUser().getUserId(), artistUserId)) {
            throw ApiException.notFound("문의를 찾을 수 없어요."); // 남의 문의는 있는지도 알려주지 않음
        }
        return inquiry;
    }

    private static String normalize(String raw, String emptyMessage) {
        String v = raw == null ? "" : raw.strip();
        if (v.isEmpty()) throw ApiException.badRequest(emptyMessage);
        if (v.length() > 1000) throw ApiException.badRequest("1000자까지 보낼 수 있어요.");
        return v;
    }

    private static String limit(String text) {
        return text.length() <= 255 ? text : text.substring(0, 254) + "…";
    }

    private InquiryResponse toResponse(LessonInquiry i, Long me) {
        ArtistProfile ap = i.getArtistProfile();
        return new InquiryResponse(
                i.getInquiryId(),
                Objects.equals(ap.getUser().getUserId(), me) ? "ARTIST" : "SENDER",
                ap.getArtistProfileId(),
                ap.getUser().getDisplayName(),
                ap.getUser().getProfileImgUrl(),
                i.getUser().getUserId(),
                i.getUser().getDisplayName(),
                i.getUser().getProfileImgUrl(),
                i.getContent(),
                i.getReplyContent(),
                i.getRepliedDt(),
                i.getStatus().name(),
                i.getChatRoomId(),
                i.getCreateDt()
        );
    }
}
