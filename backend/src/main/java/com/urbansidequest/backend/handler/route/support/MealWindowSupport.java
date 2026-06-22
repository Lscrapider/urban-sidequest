package com.urbansidequest.backend.handler.route.support;

import com.urbansidequest.backend.domain.enums.MealWindow;
import com.urbansidequest.backend.domain.param.RouteGenerateParam;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public final class MealWindowSupport {

    private static final LocalTime LUNCH_START = LocalTime.of(11, 30);

    private static final LocalTime LUNCH_END = LocalTime.of(13, 30);

    private static final LocalTime DINNER_START = LocalTime.of(17, 30);

    private static final LocalTime DINNER_END = LocalTime.of(20, 0);

    private MealWindowSupport() {
    }

    public static List<MealWindow> selectedMealWindows(RouteGenerateParam param) {
        if (param == null || param.getMealWindows() == null) {
            return List.of();
        }
        return param.getMealWindows();
    }

    public static boolean requiresLunch(RouteGenerateParam param) {
        return selectedMealWindows(param).contains(MealWindow.LUNCH);
    }

    public static boolean requiresDinner(RouteGenerateParam param) {
        return selectedMealWindows(param).contains(MealWindow.DINNER);
    }

    public static List<MealWindow> feasibleMealWindows(RouteGenerateParam param) {
        if (param == null) {
            return List.of();
        }
        return feasibleMealWindows(param.getDepartureTime(), param.getDurationMinutes());
    }

    public static List<MealWindow> feasibleMealWindows(LocalDateTime departureTime, Integer durationMinutes) {
        if (departureTime == null || durationMinutes == null || durationMinutes <= 0) {
            return List.of();
        }
        List<MealWindow> result = new ArrayList<>();
        if (overlaps(departureTime, durationMinutes, LUNCH_START, LUNCH_END)) {
            result.add(MealWindow.LUNCH);
        }
        if (overlaps(departureTime, durationMinutes, DINNER_START, DINNER_END)) {
            result.add(MealWindow.DINNER);
        }
        return result;
    }

    public static String startText(MealWindow mealWindow) {
        return switch (mealWindow) {
            case LUNCH -> LUNCH_START.toString();
            case DINNER -> DINNER_START.toString();
        };
    }

    public static String endText(MealWindow mealWindow) {
        return switch (mealWindow) {
            case LUNCH -> LUNCH_END.toString();
            case DINNER -> DINNER_END.toString();
        };
    }

    private static boolean overlaps(LocalDateTime departureTime, int durationMinutes, LocalTime start, LocalTime end) {
        LocalDateTime routeStart = departureTime;
        LocalDateTime routeEnd = routeStart.plusMinutes(durationMinutes);
        LocalDate cursorDate = routeStart.toLocalDate();
        while (!cursorDate.isAfter(routeEnd.toLocalDate())) {
            LocalDateTime windowStart = LocalDateTime.of(cursorDate, start);
            LocalDateTime windowEnd = LocalDateTime.of(cursorDate, end);
            if (routeStart.isBefore(windowEnd) && routeEnd.isAfter(windowStart)) {
                return true;
            }
            cursorDate = cursorDate.plusDays(1);
        }
        return false;
    }
}
