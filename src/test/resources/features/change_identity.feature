Feature: Change identity and evidence association
  A Change's identity is keyed on what it structurally represents (its
  symbol-set and transformation-shape), not on where it happens to sit in
  a diff — so the same conceptual Change survives a force-push or rewritten
  commit history. Every Change also exposes the files/symbols/lines it
  touches and the underlying textual diff, regardless of classification.

  Scenario: A Change's identity is derived from its symbol-set and transformation shape
    Given a Change representing a rename from "User#login" to "Account#login"
    When the semantic engine computes the Change's identity key
    Then the identity key reflects the rename transformation shape
    And the identity key reflects the "User#login" and "Account#login" symbols

  Scenario: The same conceptual Change keeps the same identity after rewritten history
    Given a Change representing a rename from "User#login" to "Account#login" detected in revision A
    And the same conceptual rename detected again in revision B after history was rewritten
    When the semantic engine computes both Changes' identity keys
    Then both identity keys are equal

  Scenario: A structurally different Change gets a different identity
    Given a Change representing a rename from "User#login" to "Account#login"
    And a Change representing a move of "User#login" to "Account#login"
    When the semantic engine computes both Changes' identity keys
    Then the identity keys are different

  Scenario: Looking up a Change by identity key across two independently-computed sets
    Given a set of Changes computed from revision A, including a rename from "User#login" to "Account#login"
    And a set of Changes computed from revision B, including the same conceptual rename
    When the semantic engine looks up the revision-A Change's identity key in the revision-B set
    Then the matching revision-B Change is found

  Scenario: A Change exposes its evidence regardless of classification
    Given a Change representing a rename from "User#login" to "Account#login" touching file "User.java" with diff "renamed login to login"
    When someone inspects the Change's evidence
    Then the evidence includes the file "User.java"
    And the evidence includes the symbols "User#login" and "Account#login"
    And the evidence includes the underlying textual diff
