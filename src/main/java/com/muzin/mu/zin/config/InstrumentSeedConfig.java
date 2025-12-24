//package com.muzin.mu.zin.config;
//
//// db에 시간 제약 걸려있어서 차라리 seed로 넣는게 나음 - 이건 나중에 한번 더 생각해볼 것
//
//import com.muzin.mu.zin.entity.instrument.Instrument;
//import com.muzin.mu.zin.entity.instrument.InstrumentCategory;
//import com.muzin.mu.zin.repository.InstrumentRepository;
//import lombok.RequiredArgsConstructor;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.util.List;
//
//@Configuration
//@RequiredArgsConstructor
//public class InstrumentSeedConfig {
//
//    private final InstrumentRepository instrumentRepository;
//    private record Seed(String name, InstrumentCategory category) {}
//
//    @Bean
//    public CommandLineRunner seedInstruments() {
//        return args -> {
//            // 이미 데이터 있으면 스킵(중복 삽입 방지)
//            if (instrumentRepository.count() > 0) return;
//            List<Seed> seeds = List.of(
//                    // 목관
//                    new Seed("플루트", InstrumentCategory.WOODWIND),
//                    new Seed("오보에", InstrumentCategory.WOODWIND),
//                    new Seed("클라리넷", InstrumentCategory.WOODWIND),
//                    new Seed("바순", InstrumentCategory.WOODWIND),
//                    new Seed("색소폰", InstrumentCategory.WOODWIND),
//
//                    // 금관
//                    new Seed("트럼펫", InstrumentCategory.BRASS),
//                    new Seed("호른", InstrumentCategory.BRASS),
//                    new Seed("트롬본", InstrumentCategory.BRASS),
//                    new Seed("튜바", InstrumentCategory.BRASS),
//                    new Seed("유포늄", InstrumentCategory.BRASS),
//
//                    // 현악
//                    new Seed("바이올린", InstrumentCategory.STRINGS),
//                    new Seed("비올라", InstrumentCategory.STRINGS),
//                    new Seed("첼로", InstrumentCategory.STRINGS),
//                    new Seed("더블베이스", InstrumentCategory.STRINGS),
//                    new Seed("기타", InstrumentCategory.STRINGS),
//                    new Seed("일렉기타", InstrumentCategory.STRINGS),
//                    new Seed("베이스기타", InstrumentCategory.STRINGS),
//                    new Seed("우쿨렐레", InstrumentCategory.STRINGS),
//
//                    // 건반
//                    new Seed("피아노", InstrumentCategory.KEYBOARD),
//                    new Seed("오르간", InstrumentCategory.KEYBOARD),
//                    new Seed("키보드", InstrumentCategory.KEYBOARD),
//
//
//                    // 타악
//                    new Seed("드럼", InstrumentCategory.PERCUSSION),
//                    new Seed("타악기", InstrumentCategory.PERCUSSION),
//                    new Seed("마림바", InstrumentCategory.PERCUSSION),
//
//                    // 국악기
//                    new Seed("가야금", InstrumentCategory.KOREAN_TRADITIONAL),
//                    new Seed("거문고", InstrumentCategory.KOREAN_TRADITIONAL),
//                    new Seed("해금", InstrumentCategory.KOREAN_TRADITIONAL),
//                    new Seed("대금", InstrumentCategory.KOREAN_TRADITIONAL),
//                    new Seed("피리", InstrumentCategory.KOREAN_TRADITIONAL),
//                    new Seed("장구", InstrumentCategory.KOREAN_TRADITIONAL),
//                    new Seed("북", InstrumentCategory.KOREAN_TRADITIONAL),
//
//                    // ✅ 보컬/성악
//                    new Seed("보컬", InstrumentCategory.VOCAL),
//                    new Seed("성악", InstrumentCategory.VOCAL)
//            );
//
//            for (Seed s : seeds) {
//                if (!instrumentRepository.existsByInstName(s.name())) {
//                    instrumentRepository.save(
//                            Instrument.builder()
//                                    .instName(s.name())
//                                    .category(s.category())
//                                    .build()
//                    );
//                }
//            }
//
//        };
//    }
//}
// 이거 sql로 넣어서 쓸모없음