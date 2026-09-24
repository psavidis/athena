Feature: Contract annotation changes have their own structural concept
  A changed annotation on a method, its return type or its parameters changes
  the method's contract, not its signature, and the Explorer says so (ticket
  #296; #258 re-run report, N5).

  Scenario: A parameter annotation change reads as a contract annotation change
    Given a method whose parameter gains "@Nullable"
    When Athena classifies the change
    Then its Structural classification is "Change Contract Annotations"

  Scenario: A method annotation change reads as a contract annotation change
    Given a method that gains "@Deprecated"
    When Athena classifies the change
    Then its Structural classification is "Change Contract Annotations"

  Scenario: A signature change still reads as a signature change
    Given a method that gains a parameter
    When Athena classifies the change
    Then its Structural classification is "Change Signature"
