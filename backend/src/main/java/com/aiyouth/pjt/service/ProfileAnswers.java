package com.aiyouth.pjt.service;

import com.aiyouth.pjt.dto.response.PlanningResponse.*;
import com.aiyouth.pjt.global.common.RequestFields;
import com.aiyouth.pjt.model.PlanningRows.ProfileRow;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

final class ProfileAnswers {
    static final String[] FIELDS = {"birthYear", "birthdayPassedThisYear", "employmentStatus", "frequentPlace",
            "availableCashWon", "income", "commuteLimit", "moveIn", "ownsHome", "parentAddressStatus"};
    private static final String BIRTH_YEAR = "birthYear";
    private static final String BIRTHDAY = "birthdayPassedThisYear";
    private static final String ANSWER_DATE = "birthdayAnswerAsOfDate";

    private ProfileAnswers() {}

    static ObjectNode read(ObjectMapper json, ProfileRow row) {
        return row == null || row.additionalAnswers() == null ? json.createObjectNode()
                : (ObjectNode) json.readTree(row.additionalAnswers());
    }

    static void patch(RequestFields request, ObjectNode answers, LocalDate date, ObjectMapper json) {
        Integer previousYear = integer(answers, BIRTH_YEAR);
        if (request.has(BIRTH_YEAR)) {
            Integer birthYear = request.blank(BIRTH_YEAR) ? null : request.nonNegativeInt(BIRTH_YEAR);
            if (birthYear != null && birthYear > date.getYear()) {
                throw request.invalid(BIRTH_YEAR, "OUT_OF_RANGE");
            }
            set(answers, BIRTH_YEAR, birthYear, json);
            if (!Objects.equals(previousYear, birthYear)) {
                answers.remove(BIRTHDAY);
                answers.remove(ANSWER_DATE);
            }
        }
        if (request.has(BIRTHDAY)) {
            Boolean passed = request.blank(BIRTHDAY) ? null : request.bool(BIRTHDAY);
            if (passed != null && (integer(answers, BIRTH_YEAR) == null
                    || (!passed && integer(answers, BIRTH_YEAR) == date.getYear()))) {
                throw request.invalid(BIRTHDAY, integer(answers, BIRTH_YEAR) == null ? "BIRTH_YEAR_REQUIRED" : "OUT_OF_RANGE");
            }
            set(answers, BIRTHDAY, passed, json);
            set(answers, ANSWER_DATE, passed == null ? null : date, json);
        }
        if (request.has("income")) {
            Income income = null;
            if (!request.blank("income")) {
                RequestFields input = request.nested("income", "period", "amountWon");
                income = new Income(input.oneOf("period", "MONTHLY", "ANNUAL"), input.nonNegativeLong("amountWon"));
            }
            set(answers, "income", income, json);
        }
        if (request.has("commuteLimit")) {
            CommuteLimit limit = null;
            if (!request.blank("commuteLimit")) {
                RequestFields input = request.nested("commuteLimit", "type", "minutes");
                String type = input.oneOf("type", "LIMIT", "NO_LIMIT");
                if (type.equals("NO_LIMIT") && input.has("minutes")) {
                    throw input.invalid("minutes", "UNKNOWN_FIELD");
                }
                limit = new CommuteLimit(type, type.equals("LIMIT") ? input.nonNegativeInt("minutes") : null);
            }
            set(answers, "commuteLimit", limit, json);
        }
        if (request.has("moveIn")) {
            String type = request.blank("moveIn") ? null : request.nested("moveIn", "type")
                    .oneOf("type", "THIS_MONTH", "NEXT_MONTH", "WITHIN_THREE_MONTHS", "UNDECIDED");
            set(answers, "moveIn", type == null ? null : moveIn(type, date), json);
        }
        if (request.has("ownsHome")) {
            set(answers, "ownsHome", request.blank("ownsHome") ? null : request.bool("ownsHome"), json);
        }
        if (request.has("parentAddressStatus")) {
            set(answers, "parentAddressStatus", request.blank("parentAddressStatus") ? null :
                    request.oneOf("parentAddressStatus", "SAME", "DIFFERENT", "UNKNOWN"), json);
        }
    }

    static Profile profile(ProfileRow row, Location place, LocalDate date, ObjectMapper json) {
        ObjectNode answers = read(json, row);
        Integer birthYear = integer(answers, BIRTH_YEAR);
        LocalDate answerDate = answers.path(ANSWER_DATE).isString()
                ? LocalDate.parse(answers.path(ANSWER_DATE).asString()) : null;
        Boolean storedAnswer = bool(answers, BIRTHDAY);
        Boolean currentAnswer = effectiveBirthday(storedAnswer, answerDate, date);
        Age age = age(birthYear, currentAnswer, date);
        Income income = object(answers, "income", Income.class, json);
        if (income == null && row != null && row.monthlyIncomeWon() != null && !answers.has("income")) {
            income = new Income("MONTHLY", row.monthlyIncomeWon());
        }
        CommuteLimit commute = object(answers, "commuteLimit", CommuteLimit.class, json);
        if (commute == null && row != null && row.maxCommuteMinutes() != null && !answers.has("commuteLimit")) {
            commute = new CommuteLimit("LIMIT", row.maxCommuteMinutes());
        }
        return new Profile(birthYear, currentAnswer, answerDate, row == null ? null : row.employmentStatus(),
                place, row == null ? null : row.availableCashWon(), income, commute,
                object(answers, "moveIn", MoveIn.class, json), bool(answers, "ownsHome"),
                answers.path("parentAddressStatus").isString() ? answers.path("parentAddressStatus").asString() : null,
                age, monthlyIncome(income));
    }

    static Age age(Integer birthYear, Boolean answer, LocalDate date) {
        if (birthYear == null || birthYear > date.getYear()) {
            return null;
        }
        int upper = date.getYear() - birthYear;
        int lower = Math.max(upper - 1, 0);
        if (answer == null) {
            return new Age(lower, upper, null);
        }
        int exact = answer ? upper : lower;
        return new Age(exact, exact, exact);
    }

    static Boolean effectiveBirthday(Boolean answer, LocalDate answerDate, LocalDate date) {
        if (answerDate == null || answerDate.getYear() != date.getYear() || answerDate.isAfter(date)) {
            return null;
        }
        return answerDate.equals(date) || Boolean.TRUE.equals(answer) ? answer : null;
    }

    static Progress progress(Profile profile, int homeCount) {
        Object[] values = {profile.birthYear(), profile.employmentStatus(), profile.frequentPlace(),
                profile.availableCashWon(), profile.income(), profile.commuteLimit(), profile.moveIn(),
                profile.ownsHome(), profile.parentAddressStatus()};
        String[] names = {"birthYear", "employmentStatus", "frequentPlace", "availableCashWon", "income",
                "commuteLimit", "moveIn", "ownsHome", "parentAddressStatus"};
        List<String> missing = new ArrayList<>();
        for (int index = 0; index < names.length; index++) {
            if (values[index] == null) {
                missing.add(names[index]);
            }
        }
        return new Progress(names.length - missing.size(), names.length, List.copyOf(missing),
                missing.isEmpty(), missing.isEmpty() && homeCount >= 2);
    }

    private static MoveIn moveIn(String type, LocalDate date) {
        YearMonth month = YearMonth.from(date);
        return switch (type) {
            case "THIS_MONTH" -> new MoveIn(type, date, month.toString(), month.toString());
            case "NEXT_MONTH" -> new MoveIn(type, date, month.plusMonths(1).toString(), month.plusMonths(1).toString());
            case "WITHIN_THREE_MONTHS" -> new MoveIn(type, date, month.toString(), month.plusMonths(3).toString());
            default -> new MoveIn(type, date, null, null);
        };
    }

    private static Long monthlyIncome(Income income) {
        if (income == null) {
            return null;
        }
        if (income.period().equals("MONTHLY")) {
            return income.amountWon();
        }
        // Round to the closest whole won without overflowing the original annual amount.
        return income.amountWon() / 12 + (income.amountWon() % 12 >= 6 ? 1 : 0);
    }

    private static Integer integer(ObjectNode answers, String name) {
        JsonNode value = answers.path(name);
        return value.isIntegralNumber() && value.canConvertToInt() ? value.asInt() : null;
    }

    private static Boolean bool(ObjectNode answers, String name) {
        JsonNode value = answers.path(name);
        return value.isBoolean() ? value.asBoolean() : null;
    }

    private static <T> T object(ObjectNode answers, String key, Class<T> type, ObjectMapper json) {
        JsonNode value = answers.path(key);
        return value.isObject() ? json.treeToValue(value, type) : null;
    }

    private static void set(ObjectNode answers, String key, Object value, ObjectMapper json) {
        if (value == null) {
            answers.remove(key);
        } else {
            answers.set(key, json.valueToTree(value));
        }
    }
}
