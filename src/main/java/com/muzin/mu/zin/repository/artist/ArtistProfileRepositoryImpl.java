package com.muzin.mu.zin.repository.artist;

import com.muzin.mu.zin.dto.artist.ArtistInstrumentRow;
import com.muzin.mu.zin.dto.artist.ArtistSearchRequest;
import com.muzin.mu.zin.dto.artist.ArtistSearchRow;
import com.muzin.mu.zin.dto.region.SearchServiceRegionRow;
import com.muzin.mu.zin.entity.ArtistStatus;
import com.muzin.mu.zin.entity.QArtistInstrument;
import com.muzin.mu.zin.entity.QArtistProfile;
import com.muzin.mu.zin.entity.QUser;
import com.muzin.mu.zin.entity.artist.QArtistServiceRegion;
import com.muzin.mu.zin.entity.artist.QArtistStyleMap;
import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.util.List;

@RequiredArgsConstructor
public class ArtistProfileRepositoryImpl implements ArtistProfileRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 존재 여부 검사 필터
    @Override
    public List<ArtistSearchRow> searchArtistRows(ArtistSearchRequest req) {

        QArtistProfile artistProfile = QArtistProfile.artistProfile;
        QUser user = QUser.user;
        QArtistInstrument artistInstrument = QArtistInstrument.artistInstrument;
        QArtistStyleMap artistStyleMap = QArtistStyleMap.artistStyleMap;


        return queryFactory
                .select(Projections.constructor(
                        ArtistSearchRow.class,
                        artistProfile.artistProfileId,
                        user.username,
                        artistProfile.majorName,
                        user.email,
                        user.profileImgUrl,
                        artistProfile.region1DepthName,
                        artistProfile.region2DepthName,
                        artistProfile.region3DepthName,
                        artistProfile.addressLabel

                ))
                .from(artistProfile)
                .join(artistProfile.user, user) // user 를 기준으로 검색조건에 맞는 아티스트들만 먼저 뽑음
                .where(
                        approvedArtistCondition(user),
                        keywordCondition(req.keyword(), user, artistProfile),
                        instCategoryCondition(req.instCategory(), artistInstrument, artistProfile),
                        instIdsCondition(req.instIds(), artistInstrument, artistProfile),
                        styleTagIdsCondition(req.styleTagIds(), artistStyleMap, artistProfile),
                        regionCondition(req, artistProfile)
                )
                .fetch();
    }

    // 위 쿼리에 부합한 사람의 카드에 어떤 악기 목록을 보여줄지
    @Override
    public List<ArtistInstrumentRow> findArtistInstrumentRows(List<Long> artistProfileIds) {
        if (artistProfileIds == null || artistProfileIds.isEmpty()) {
            return List.of();
        }

        QArtistInstrument artistInstrument = QArtistInstrument.artistInstrument;

        return queryFactory
                .select(Projections.constructor(
                        ArtistInstrumentRow.class,
                        artistInstrument.artistProfile.artistProfileId,
                        artistInstrument.instrument.instId,
                        artistInstrument.instrument.instName
                ))
                .from(artistInstrument)
                .where(artistInstrument.artistProfile.artistProfileId.in(artistProfileIds))
                .fetch();
    }

    @Override
    public List<SearchServiceRegionRow> findArtistServiceRegionRows(List<Long> artistProfileIds) {
        if (artistProfileIds == null || artistProfileIds.isEmpty()) {
            return List.of();
        }

        QArtistServiceRegion artistServiceRegion = QArtistServiceRegion.artistServiceRegion;

        return queryFactory
                .select(Projections.constructor(
                        SearchServiceRegionRow.class,
                        artistServiceRegion.artistProfile.artistProfileId,
                        artistServiceRegion.region1DepthName,
                        artistServiceRegion.region2DepthName,
                        artistServiceRegion.region3DepthName
                ))
                .from(artistServiceRegion)
                .where(artistServiceRegion.artistProfile.artistProfileId.in(artistProfileIds))
                .fetch();

    }

    // 유저의 아티스트 요청 상황이 approved 인지(아티스트만 검색 가능해야함)
    private BooleanExpression approvedArtistCondition(QUser user) {
        return user.artistStatus.eq(ArtistStatus.APPROVED);
    }

    // input 검색 키워드 조건 정리(아티스트(유저)명, 전공명)
    private BooleanExpression keywordCondition(
            String keyword,
            QUser user,
            QArtistProfile artistProfile
    ) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return null;
        }

        String trimmed = keyword.trim();
        return user.username.containsIgnoreCase(trimmed)
                .or(artistProfile.majorName.containsIgnoreCase(trimmed));
    }

    // 악기 카테고리 검색
    private BooleanExpression instCategoryCondition(
        InstrumentCategory instCategory,
        QArtistInstrument artistInstrument,
        QArtistProfile artistProfile
    ) {
        if (instCategory == null) {
            return null;
        }

        return JPAExpressions
                .selectOne()
                .from(artistInstrument)
                .where(
                        artistInstrument.artistProfile.eq(artistProfile),
                        artistInstrument.instrument.category.eq(instCategory)
                )
                .exists();
    }

    // 악기 상세 검색
    private BooleanExpression instIdsCondition(
            List<Long> instIds,
            QArtistInstrument artistInstrument,
            QArtistProfile artistProfile
    ) {
        if (instIds == null || instIds.isEmpty()) {
            return null;
        }

        return JPAExpressions
                .selectOne()
                .from(artistInstrument)
                .where(
                        artistInstrument.artistProfile.eq(artistProfile),
                        artistInstrument.instrument.instId.in(instIds)
                )
                .exists();
    }

    // 스타일 태그 검색
    private BooleanExpression styleTagIdsCondition(
            List<Long> styleTagIds,
            QArtistStyleMap artistStyleMap,
            QArtistProfile artistProfile
    ) {
        if (styleTagIds == null || styleTagIds.isEmpty()) {
            return null;
        }

        return JPAExpressions
                .selectOne()
                .from(artistStyleMap)
                .where(
                        artistStyleMap.artistProfile.eq(artistProfile),
                        artistStyleMap.lessonStyleTag.lessonStyleTagId.in(styleTagIds)
                )
                .exists();
    }

    // 지역 검색
    private BooleanExpression regionCondition(
            ArtistSearchRequest req,
            QArtistProfile artistProfile
    ) {
        String region1 = req.region1DepthName();
        String region2 = req.region2DepthName();
        String region3 = req.region3DepthName();

        if (region1 == null || region1.trim().isEmpty()) {
            return null;
        }

        BooleanExpression serviceRegionExpr =
                serviceRegionCondition(region1, region2, region3, artistProfile);

        BooleanExpression mainRegionExpr =
                mainRegionCondition(region1, region2, region3, artistProfile);

        if (serviceRegionExpr == null) return mainRegionExpr;
        if (mainRegionExpr == null) return serviceRegionExpr;

        return serviceRegionExpr.or(mainRegionExpr);
    }

    // 서비스 지역
    private BooleanExpression serviceRegionCondition(
            String region1,
            String region2,
            String region3,
            QArtistProfile artistProfile
    ) {
        QArtistServiceRegion artistServiceRegion = QArtistServiceRegion.artistServiceRegion;

        BooleanExpression condition = artistServiceRegion.artistProfile.eq(artistProfile)
                .and(artistServiceRegion.region1DepthName.eq(region1));

        if (region2 != null && !region2.trim().isEmpty()) {
            condition = condition.and(artistServiceRegion.region2DepthName.eq(region2));
        }

        if (region3 != null && !region3.trim().isEmpty()) {
            condition = condition.and(artistServiceRegion.region3DepthName.eq(region3));
        }

        return JPAExpressions
                .selectOne()
                .from(artistServiceRegion)
                .where(condition)
                .exists();
    }


    // 주 활동 지역(스튜디오)
    private BooleanExpression mainRegionCondition(
            String region1,
            String region2,
            String region3,
            QArtistProfile artistProfile
    ) {
        BooleanExpression condition = artistProfile.region1DepthName.eq(region1);

        if (region2 != null && !region2.trim().isEmpty()) {
            condition = condition.and(artistProfile.region2DepthName.eq(region2));
        }

        if (region3 != null && !region3.trim().isEmpty()) {
            condition = condition.and(artistProfile.region3DepthName.eq(region3));
        }

        return condition;
    }
}
