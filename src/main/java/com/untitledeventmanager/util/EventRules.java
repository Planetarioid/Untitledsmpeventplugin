package com.untitledeventmanager.util;

import java.util.Set;

/** Pure validation rules shared by commands, menus, and tests. */
public final class EventRules {
    private static final Set<String> END_PLACEHOLDERS = Set.of(
            "{player}", "{lobby_world}", "{lobby_x}", "{lobby_y}", "{lobby_z}",
            "{lobby_yaw}", "{lobby_pitch}"
    );

    private EventRules() { }

    public static boolean validKitName(String name) {
        return name != null && name.matches("[A-Za-z0-9_-]{1,32}");
    }

    public static boolean validCapacity(int amount, int maximum) {
        return maximum >= 1 && amount >= 1 && amount <= maximum;
    }

    public static boolean validPlayerName(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{1,16}");
    }

    public static boolean validEndCommand(String command) {
        if (command == null || command.isBlank() || command.length() > 256
                || command.contains("\n") || command.contains("\r")) return false;
        String remainder = command;
        for (String placeholder : END_PLACEHOLDERS) remainder = remainder.replace(placeholder, "");
        return !remainder.contains("{") && !remainder.contains("}");
    }
}
