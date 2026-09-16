package com.athena.contextrewind;

/**
 * One labeled piece of information in a {@link ReconstructedContext}
 * (ticket #161): what was found, and which {@link ContextSource} it came
 * from, so a caller never has to guess whether something is a project
 * fact, external knowledge, or Athena's own interpretation.
 */
public record ContextFact(String description, ContextSource source) {
}
