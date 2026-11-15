package com.untitledeventmanager.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EventRulesTest {
    @Test void kitNamesAreBoundedAndSafe() {
        assertTrue(EventRules.validKitName("Tournament_Kit-1"));
        assertFalse(EventRules.validKitName(""));
        assertFalse(EventRules.validKitName("kit with spaces"));
        assertFalse(EventRules.validKitName("../config"));
        assertFalse(EventRules.validKitName("x".repeat(33)));
    }

    @Test void capacityMustBeWithinConfiguredBounds() {
        assertTrue(EventRules.validCapacity(1, 500));
        assertTrue(EventRules.validCapacity(500, 500));
        assertFalse(EventRules.validCapacity(0, 500));
        assertFalse(EventRules.validCapacity(501, 500));
    }

    @Test void playerNamesCannotInjectCommandSyntax() {
        assertTrue(EventRules.validPlayerName("Player_123"));
        assertFalse(EventRules.validPlayerName("Player;op"));
        assertFalse(EventRules.validPlayerName("player name"));
        assertFalse(EventRules.validPlayerName("x".repeat(17)));
    }

    @Test void endCommandsAcceptOnlyKnownPlaceholders() {
        assertTrue(EventRules.validEndCommand("warp {player} {lobby_world} {lobby_x}"));
        assertTrue(EventRules.validEndCommand("say Event finished"));
        assertFalse(EventRules.validEndCommand("say {unknown}"));
        assertFalse(EventRules.validEndCommand("say hello\nop player"));
        assertFalse(EventRules.validEndCommand(" "));
        assertFalse(EventRules.validEndCommand("x".repeat(257)));
    }
}
