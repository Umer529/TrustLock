package com.example.trustlock.models;

/**
 * Which side of the dual-role app a signed-in user is acting as.
 * Stored as the literal string name in Supabase {@code users.role}.
 */
public enum Role {
    USER,
    GUARDIAN,
    BOTH;

    public static Role fromString(String s) {
        if (s == null) return null;
        try {
            return Role.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
