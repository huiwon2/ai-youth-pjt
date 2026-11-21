package com.aiyouth.pjt.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.aiyouth.pjt.global.exception.ApiException;
import com.aiyouth.pjt.global.exception.ErrorCode;
import com.aiyouth.pjt.global.revision.Revisions;

class RevisionsTest {

    @Test
    void If_Match_형식() {
        assertThat(Revisions.parseIfMatch("\"6\"")).isEqualTo(6);
        assertThat(Revisions.parseIfMatch("6")).isEqualTo(6);
        assertThat(Revisions.parseIfMatch("W/\"6\"")).isEqualTo(6);
        assertThatThrownBy(() -> Revisions.parseIfMatch(null)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.PRECONDITION_REQUIRED));
        assertThatThrownBy(() -> Revisions.parseIfMatch("abc")).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.REVISION_MISMATCH));
    }

    @Test
    void 오래된_입력은_STALE_INPUT() {
        assertThatThrownBy(() -> Revisions.requireCurrentInput(4, 5)).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.code()).isEqualTo(ErrorCode.STALE_INPUT));
    }
}
