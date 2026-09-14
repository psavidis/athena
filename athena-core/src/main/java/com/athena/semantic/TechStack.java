package com.athena.semantic;

/**
 * A module's real technology, read from its own manifest (ticket #129's
 * tech-stack badge) — never guessed from the module's name.
 */
public enum TechStack {
    SPRING_BOOT_JAVA("Spring Boot · Java"),
    JAVA("Java"),
    REACT_TYPESCRIPT("React · TypeScript"),
    TYPESCRIPT("TypeScript"),
    UNKNOWN("Unknown");

    private final String label;

    TechStack(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
