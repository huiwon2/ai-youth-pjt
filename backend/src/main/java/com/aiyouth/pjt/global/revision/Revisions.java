package com.aiyouth.pjt.global.revision;

import com.aiyouth.pjt.global.exception.ApiException;

public final class Revisions {
    private Revisions() {}

    public static void requireMatch(String header, int revision) {
        if (header == null) {
            throw new ApiException(428, "PRECONDITION_REQUIRED", "If-Match에 현재 입력 버전을 보내 주세요.", false);
        }
        if (!header.equals("\"" + revision + "\"")) {
            throw new ApiException(412, "REVISION_MISMATCH", "입력이 바뀌었어요. 최신 상태를 다시 열어 주세요.", false);
        }
    }

    public static void requireInput(int inputRevision, int revision) {
        if (inputRevision != revision) {
            throw new ApiException(409, "STALE_INPUT", "입력이 바뀌었어요. 최신 상태로 다시 시도해 주세요.", false);
        }
    }
}
