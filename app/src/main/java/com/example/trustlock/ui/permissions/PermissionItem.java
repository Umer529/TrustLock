package com.example.trustlock.ui.permissions;

public class PermissionItem {

    public enum Type {
        USAGE_ACCESS,
        ACCESSIBILITY,
        OVERLAY,
        DEVICE_ADMIN,
        NOTIFICATIONS
    }

    private final Type type;
    private final String name;
    private final String description;
    private final int iconRes;
    private boolean granted;

    public PermissionItem(Type type, String name, String description, int iconRes) {
        this.type = type;
        this.name = name;
        this.description = description;
        this.iconRes = iconRes;
        this.granted = false;
    }

    public Type getType() { return type; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getIconRes() { return iconRes; }
    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }
}
