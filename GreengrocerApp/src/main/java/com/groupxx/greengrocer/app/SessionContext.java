package com.groupxx.greengrocer.app;

import com.groupxx.greengrocer.model.Role;
import com.groupxx.greengrocer.model.UserRecord;

public final class SessionContext {
    private static String username;
    private static Role role;
    private static UserRecord user; // optional (if you have it)

    private SessionContext() {
    }

    // OLD style (your existing code uses this) ✅
    public static void set(String username, Role role) {
        SessionContext.username = trim(username);
        SessionContext.role = role;
        SessionContext.user = null;
    }

    // NEW style ✅
    public static void setUser(UserRecord u) {
        SessionContext.user = u;
        if (u != null) {
            SessionContext.username = trim(u.username());
            SessionContext.role = u.role();
        } else {
            SessionContext.username = null;
            SessionContext.role = null;
        }
    }

    // Backward-compatible aliases (in case other files call different names)
    public static void setCurrentUser(UserRecord u) {
        setUser(u);
    }

    public static void setUserRecord(UserRecord u) {
        setUser(u);
    }

    public static String getUsername() {
        return username;
    }

    public static Role getRole() {
        return role;
    }

    public static UserRecord getUser() {
        return user;
    }

    public static void setUsername(String newUsername) {
        username = trim(newUsername);
    }

    // Backward-compatible aliases for older code
    public static String username() {
        return getUsername();
    }

    public static Role role() {
        return getRole();
    }

    public static String requireUsername() {
        if (username == null || username.isBlank())
            throw new IllegalStateException("No username in session.");
        return username;
    }

    public static Role requireRole() {
        if (role == null)
            throw new IllegalStateException("No role in session.");
        return role;
    }

    public static void clear() {
        username = null;
        role = null;
        user = null;
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }
}
