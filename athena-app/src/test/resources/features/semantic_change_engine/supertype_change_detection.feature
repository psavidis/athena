Feature: Changed supertypes
  A class whose superclass or implemented interfaces change can change its
  whole contract and behavior through them, so a changed extends or implements
  list is a Change of its own (ticket #358; third expansion baseline, K4).

  Scenario: A replaced superclass is reported with both types
    Given the reviewer has selected a PR where class "SimpleRegistry" changed from "extends java.util.LinkedHashMap<String, Object>" to "extends java.util.concurrent.ConcurrentHashMap<String, Object>"
    When the reviewer views the Change Map of that PR
    Then the Change Map includes the Change "Change supertype of SimpleRegistry: LinkedHashMap -> ConcurrentHashMap"

  Scenario: An added interface is reported
    Given the reviewer has selected a PR where class "Registry" changed from "" to "implements java.io.Closeable"
    When the reviewer views the Change Map of that PR
    Then the Change Map includes the Change "Change supertype of Registry: +Closeable"

  Scenario: A replaced superclass and a removed interface are reported together
    Given the reviewer has selected a PR where class "Registry" changed from "extends java.util.LinkedHashMap<String, Object> implements java.io.Serializable" to "extends java.util.concurrent.ConcurrentHashMap<String, Object>"
    When the reviewer views the Change Map of that PR
    Then the Change Map includes the Change "Change supertypes of Registry: LinkedHashMap -> ConcurrentHashMap, -Serializable"

  Scenario: A change of type arguments only is not a supertype change
    Given the reviewer has selected a PR where class "Names" changed from "extends java.util.ArrayList<String>" to "extends java.util.ArrayList<CharSequence>"
    When the reviewer views the Change Map of that PR
    Then no Change in the Change Map mentions "supertype"

  Scenario: The same interface added to several classes is one entry
    Given the reviewer has selected a PR where classes "PoolA", "PoolB" and "PoolC" each started implementing "java.io.Closeable"
    When the reviewer opens the PR-level semantic profile
    Then the Structural entries include one "Change Supertype +Closeable in 3 classes" entry folding 3 changes
