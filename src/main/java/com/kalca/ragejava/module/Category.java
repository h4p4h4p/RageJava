package com.kalca.ragejava.module;

public enum Category {

    MOVEMENT("Movement"),
    COMBAT("Combat"),
    RENDER("Render"),
    INTERFACE("Interface");

    private final String name;

    Category(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}