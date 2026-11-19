package ru.anomaly.weather.util;

/** Форматирование времени. */
public final class TimeUtil {

    private TimeUtil() {
    }

    public static String format(int totalSeconds) {
        int seconds = Math.max(0, totalSeconds);
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int rest = seconds % 60;
        if (hours > 0) {
            return hours + " ч " + (minutes < 10 ? "0" : "") + minutes + " мин";
        }
        if (minutes > 0) {
            return minutes + " мин " + (rest < 10 ? "0" : "") + rest + " сек";
        }
        return rest + " сек";
    }
}
