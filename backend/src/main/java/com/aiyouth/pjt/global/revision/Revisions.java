package com.aiyouth.pjt.global.revision;

import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.global.exception.ErrorCode;

/** 세션 revision 비교 규칙 (API 명세 「공통 전송 규칙」). */
public final class Revisions {

    private Revisions() {
    }

    /** If-Match: "6" 또는 6 → 6. 형식이 틀리면 412. */
    public static int parseIfMatch(String header) {
        if (header == null || header.isBlank()) {
            throw new ApiException(ErrorCode.PRECONDITION_REQUIRED, "최신 입력 버전을 If-Match 헤더로 보내 주세요.");
        }
        String value = header.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2);
        }
        value = value.replace("\"", "");
        try {
            int revision = Integer.parseInt(value);
            if (revision < 0) {
                throw new NumberFormatException();
            }
            return revision;
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.REVISION_MISMATCH, "입력 버전 형식이 올바르지 않아요.");
        }
    }

    /** 계산·질의 요청의 inputRevision이 현재 세션 revision과 다르면 409 STALE_INPUT. */
    public static void requireCurrentInput(int inputRevision, int sessionRevision) {
        if (inputRevision != sessionRevision) {
            throw new ApiException(ErrorCode.STALE_INPUT,
                    "입력이 바뀌었어요. 최신 내용으로 다시 요청해 주세요. (현재 버전 " + sessionRevision + ")");
        }
    }
}
