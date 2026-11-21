package com.aiyouth.pjt.dto.response.policy;

import com.aiyouth.pjt.model.RagChunk;

/** 근거 문장. id는 rag_chunks.id. */
public record Evidence(String id, String policyKey, String title, String excerpt, String sourceUrl, String locator) {

    private static final int EXCERPT_MAX = 300;

    public static Evidence from(RagChunk chunk) {
        String title = chunk.getPolicyName() != null ? chunk.getPolicyName() + " · " + chunk.getSection()
                : chunk.getSection();
        String content = chunk.getContent();
        String excerpt = content.length() > EXCERPT_MAX ? content.substring(0, EXCERPT_MAX) + "…" : content;
        return new Evidence(chunk.getId().toString(), chunk.getSourceKey(), title, excerpt, chunk.getSourceUrl(),
                chunk.getLocator());
    }
}
