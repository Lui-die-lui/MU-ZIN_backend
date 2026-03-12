package com.muzin.mu.zin.config;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

 /*
 * QueryDSL
 * JPQL 문자열을 길게 작성하는 대신 자바 코드로 조립해
 * 동적 검색을 구현하는 방식
 *
 * 아티스트 검색은 keyword, 악기, 스타일 태그 등
 * 선택적 조건이 조합되는 구조라서
 * 가독성과 추후 확장성(ex: 지역 조건 추가)을 고려해 도입함
 * */

@Configuration
public class QueryDslConfig {

    @PersistenceContext
    private EntityManager em;

    @Bean
    public JPAQueryFactory jpaQueryFactory() {
        return new JPAQueryFactory(em);
    }
}
