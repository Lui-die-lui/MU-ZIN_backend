package com.muzin.mu.zin.service.adminService;

import com.muzin.mu.zin.dto.admin.AdminArtistApplyDetail;
import com.muzin.mu.zin.dto.admin.AdminArtistApplyListItem;
import com.muzin.mu.zin.dto.instrument.InstrumentResponse;
import com.muzin.mu.zin.entity.ArtistProfile;
import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.repository.artist.ArtistProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminArtistApplyService {

    private final ArtistProfileRepository artistProfileRepository;

    // 전환 요청 리스트
    @Transactional(readOnly = true)
    public Page<AdminArtistApplyListItem> list(ArtistStatus status, Pageable pageable) {
        return artistProfileRepository
                .findAllByUser_ArtistStatusOrderBySubmittedDtDesc(status, pageable)
                .map(ap -> new AdminArtistApplyListItem(
                        ap.getArtistProfileId(),
                        ap.getUser().getUserId(),
                        ap.getUser().getEmail(),
                        ap.getUser().getUsername(),
                        ap.getUser().getArtistStatus(),
                        ap.getSubmittedDt(),
                        ap.getReviewedDt(),
                        ap.getRejectReasonTitle()
                ));
    }

    // 단건 조회
    @Transactional(readOnly = true)
    public AdminArtistApplyDetail detail(Long artistProfileId) {
        ArtistProfile ap = artistProfileRepository.findWithDetailByArtistProfileId(artistProfileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "아티스트 프로필을 찾을 수 없습니다."));

        List<InstrumentResponse> instruments = ap.getArtistInstruments().stream()
                .map(ai -> new InstrumentResponse(
                        ai.getInstrument().getInstId(),
                        ai.getInstrument().getInstName(),
                        ai.getInstrument().getCategory()
                ))
                .toList();

        return new AdminArtistApplyDetail(
                ap.getArtistProfileId(),
                ap.getUser().getUserId(),
                ap.getUser().getEmail(),
                ap.getUser().getUsername(),
                ap.getUser().getArtistStatus(),
                ap.getBio(),
                ap.getCareer(),
                ap.getMajorName(),
                ap.getSubmittedDt(),
                ap.getReviewedDt(),
                ap.getRejectReasonTitle(),
                ap.getRejectedReason(),
                instruments
        );
    }

    // 심사 통과
    public void approve(Long artistProfileId) {
        ArtistProfile ap = getPendingApplication(artistProfileId);

        ap.getUser().setArtistStatus(ArtistStatus.APPROVED);
        ap.markReviewed();
        ap.clearRejectReason();
    }

    // 심사 반려
    public void reject(Long artistProfileId, String title, String reason) {
        ArtistProfile ap = getPendingApplication(artistProfileId);

        ap.getUser().setArtistStatus(ArtistStatus.REJECTED);
        ap.markReviewed();
        ap.setRejectReason(title, reason);
    }

    private ArtistProfile getPendingApplication(Long artistProfileId) {
        ArtistProfile ap = artistProfileRepository.findById(artistProfileId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "아티스트 프로필을 찾을 수 없습니다."));


        if (ap.getSubmittedDt() == null || ap.getUser().getArtistStatus() != ArtistStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "PENDING 상태의 서류만 심사 가능합니다."
            );
        }
        return ap;
    }
}
