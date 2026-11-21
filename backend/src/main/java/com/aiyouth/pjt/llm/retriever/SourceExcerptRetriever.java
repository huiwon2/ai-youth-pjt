package com.aiyouth.pjt.llm.retriever;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aiyouth.pjt.mapper.RagChunkMapper;
import com.aiyouth.pjt.model.RagChunk;

/**
 * 검증된 공고문 문단 조회. 의미 검색은 RAG 서비스가 하고, 여기서는
 * (1) RAG가 돌려준 근거 ID의 검증 여부 확인, (2) RAG를 쓸 수 없을 때 키워드 원문 발췌만 한다.
 */
@Component
public class SourceExcerptRetriever {

    private static final int MAX_KEYWORDS = 5;
    private static final int MAX_RESULTS = 3;
    private static final List<String> PARTICLES = List.of("하나요", "할까요", "해야", "하면", "에서는", "에서", "으로", "에게", "까지", "부터", "나요",
            "까요", "인가요", "은", "는", "이", "가", "을", "를", "에", "의", "도", "로", "요");
    private static final Set<String> STOPWORDS = Set.of("정책", "지원", "언제", "어떻게", "무엇", "어디", "얼마",
            "있나요", "되나요", "하나요", "알려줘", "알려주세요", "궁금해요", "이거", "그거", "뭐야", "뭐예요", "무엇인가요");

    private final RagChunkMapper mapper;

    public SourceExcerptRetriever(RagChunkMapper mapper) {
        this.mapper = mapper;
    }

    /** VERIFIED·활성·출처 있는 문단만 남긴다. 형식이 틀린 ID는 버린다. */
    public List<RagChunk> verifiedByIds(Collection<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<UUID> uuids = new ArrayList<>();
        for (String id : ids) {
            try {
                uuids.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                // RAG가 잘못된 ID를 보내면 근거로 인정하지 않는다
            }
        }
        return uuids.isEmpty() ? List.of() : mapper.findVerifiedByIds(uuids);
    }

    public List<RagChunk> searchByKeywords(String question, String policyKey) {
        List<String> keywords = keywords(question);
        if (keywords.isEmpty()) {
            return List.of();
        }
        return mapper.searchVerifiedByKeywords(policyKey, keywords, MAX_RESULTS);
    }

    static List<String> keywords(String question) {
        Set<String> result = new LinkedHashSet<>();
        for (String raw : question.split("[^\\p{L}\\p{N}]+")) {
            String token = stripParticle(raw);
            if (token.length() >= 2 && !STOPWORDS.contains(token)) {
                result.add(token);
            }
            if (result.size() == MAX_KEYWORDS) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private static String stripParticle(String token) {
        for (String p : PARTICLES) {
            if (token.length() > p.length() + 1 && token.endsWith(p)) {
                return token.substring(0, token.length() - p.length());
            }
        }
        return token;
    }
}
