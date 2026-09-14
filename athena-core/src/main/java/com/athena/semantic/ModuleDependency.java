package com.athena.semantic;

/**
 * A real dependency edge between two module territories (ticket #129's
 * dependency rail): {@code from} declares a build/import dependency on
 * {@code to}, read from a manifest — never inferred from naming or layout.
 */
public record ModuleDependency(String from, String to) {
}
