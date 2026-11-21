package com.aiyouth.pjt.service;

import static org.junit.jupiter.api.Assertions.*;

import com.aiyouth.pjt.dto.response.PlanningResponse.Age;
import com.aiyouth.pjt.global.common.RequestFields;
import com.aiyouth.pjt.global.exception.ApiException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProfileAnswersTest {
    private final ObjectMapper json = new ObjectMapper();
    private static final LocalDate ANSWER_DATE = LocalDate.of(2026, 11, 19);

    @Test
    void birthdayBoundaryFor1991AtAssessment() {
        assertEquals(new Age(34, 34, 34), ProfileAnswers.age(1991, false, ANSWER_DATE));
        assertEquals(new Age(35, 35, 35), ProfileAnswers.age(1991, true, ANSWER_DATE));
        assertEquals(new Age(34, 35, null), ProfileAnswers.age(1991, null, ANSWER_DATE));
    }

    @Test
    void earlierFalseExpiresByTheNextDayAndTrueSurvivesForTheSameYear() {
        assertFalse(ProfileAnswers.effectiveBirthday(false, ANSWER_DATE, ANSWER_DATE));
        assertNull(ProfileAnswers.effectiveBirthday(false, ANSWER_DATE, ANSWER_DATE.plusDays(1)));
        assertTrue(ProfileAnswers.effectiveBirthday(true, ANSWER_DATE, ANSWER_DATE.plusDays(1)));
        assertNull(ProfileAnswers.effectiveBirthday(true, ANSWER_DATE, ANSWER_DATE.plusYears(1)));
        assertNull(ProfileAnswers.effectiveBirthday(true, ANSWER_DATE, ANSWER_DATE.minusDays(1)));
    }

    @Test
    void patchPreservesOmittedAnswerAndResetsItWhenBirthYearChanges() {
        var answers = json.createObjectNode().put("householdSize", 2);
        ProfileAnswers.patch(fields("""
                {"birthYear":1991,"birthdayPassedThisYear":false}
                """), answers, ANSWER_DATE, json);
        ProfileAnswers.patch(fields("""
                {"birthYear":1991,"ownsHome":false}
                """), answers, ANSWER_DATE.plusDays(1), json);
        assertFalse(answers.path("birthdayPassedThisYear").asBoolean());
        assertEquals("2026-11-19", answers.path("birthdayAnswerAsOfDate").asString());
        ProfileAnswers.patch(fields("""
                {"birthYear":1990}
                """), answers, ANSWER_DATE.plusDays(1), json);
        assertFalse(answers.has("birthdayPassedThisYear"));
        assertFalse(answers.has("birthdayAnswerAsOfDate"));
        assertEquals(2, answers.path("householdSize").asInt());
    }

    @Test
    void explicitNullClearsBothBirthdayFieldsAndSettingAnswerRequiresBirthYear() {
        var answers = json.createObjectNode();
        ApiException invalid = assertThrows(ApiException.class, () -> ProfileAnswers.patch(
                fields("{\"birthdayPassedThisYear\":true}"), answers, ANSWER_DATE, json));
        assertEquals("BIRTH_YEAR_REQUIRED", invalid.body("request").error().fields().get(0).code());
        ProfileAnswers.patch(fields("""
                {"birthYear":1991,"birthdayPassedThisYear":true}
                """), answers, ANSWER_DATE, json);
        ProfileAnswers.patch(fields("{\"birthdayPassedThisYear\":null}"), answers, ANSWER_DATE, json);
        assertFalse(answers.has("birthdayPassedThisYear"));
        assertFalse(answers.has("birthdayAnswerAsOfDate"));
        assertEquals(1991, answers.path("birthYear").asInt());
    }

    private RequestFields fields(String content) {
        return new RequestFields(json.readTree(content), ProfileAnswers.FIELDS);
    }
}
