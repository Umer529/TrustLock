package com.example.trustlock.ui.permissions;

public class PermissionItem {

    public enum Type {
        USAGE_ACCESS,
        ACCESSIBILITY,
        OVERLAY,
        DEVICE_ADMIN,
        NOTIFICATIONS
    }

    private final Type    type;
    private final String  name;
    private final String  description;
    private final int     iconRes;
    private final boolean optional;
    private       boolean granted;

    public PermissionItem(Type type, String name, String description, int iconRes,
                          boolean optional) {
        this.type        = type;
        this.name        = name;
        this.description = description;
        this.iconRes     = iconRes;
        this.optional    = optional;
        this.granted     = false;
    }

    // Back-compat overload defaulting to required.
    public PermissionItem(Type type, String name, String description, int iconRes) {
        this(type, name, description, iconRes, false);
    }

    public Type    getType()        { return type; }
    public String  getName()        { return name; }
    public String  getDescription() { return description; }
    public int     getIconRes()     { return iconRes; }
    public boolean isOptional()     { return optional; }
    public boolean isGranted()      { return granted; }

    public void setGranted(boolean granted) { this.granted = granted; }
}
