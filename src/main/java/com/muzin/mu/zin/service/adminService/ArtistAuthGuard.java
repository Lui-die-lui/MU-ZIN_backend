package com.muzin.mu.zin.service.adminService;

import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.security.model.PrincipalUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

// 승인된 아티스트 = Role 이 아닌 ENUM으로 관리되기 때문에 규칙 기반으로 관리가 힘듦
// 상태 기반 인가를 한군데로 모으기 위해서 만든 로직 - ADMIN 작업 하면서 알게된 부분이라
// 중간에 전체적으로 엎는것보다(Artist 플로우 자체를 Role 기반으로 바꾸는거)
// 해당 방법이 더욱 비용이 적게 든다 생각들어서 채택
@Component
public class ArtistAuthGuard {

    public void requireApprovedArtist(PrincipalUser principal) {
        if (principal == null || principal.getArtistStatus() != ArtistStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ARTIST_APPROVED_REQUIRED");
        }
    }
}
