package com.rezzcraft.rezzloaders;

public final class TimeUtil {
    private TimeUtil() {}

    public static String formatDuration(long ms) {
        long totalSeconds = Math.max(0L, ms / 1000L);
        long seconds = totalSeconds % 60;
        long totalMinutes = totalSeconds / 60;
        long minutes = totalMinutes % 60;
        long totalHours = totalMinutes / 60;
        long hours = totalHours % 24;
        long days = totalHours / 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append('d').append(' ');
        if (hours > 0 || days > 0) sb.append(hours).append('h').append(' ');
        if (minutes > 0 || hours > 0 || days > 0) sb.append(minutes).append('m').append(' ');
        sb.append(seconds).append('s');
        return sb.toString().trim();
    }

    public static Long parseDurationSeconds(String input) {
        if (input == null) return null;
        String s = input.trim().toLowerCase();
        if (s.isEmpty()) return null;

        try {
            // plain number means seconds
            if (s.matches("\\d+")) {
                return Long.parseLong(s);
            }
        } catch (Exception ignored) {}

        long total = 0;
        String num = "";
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isDigit(c)) {
                num += c;
                continue;
            }
            if (num.isEmpty()) return null;
            long n = Long.parseLong(num);
            num = "";
            switch (c) {
                case 's' -> total += n;
                case 'm' -> total += n * 60L;
                case 'h' -> total += n * 3600L;
                case 'd' -> total += n * 86400L;
                default -> { return null; }
            }
        }
        if (!num.isEmpty()) {
            // trailing digits without suffix = seconds
            total += Long.parseLong(num);
        }
        return total > 0 ? total : null;
    }
}
