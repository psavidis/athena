Feature: A body summary names a changed argument
  A fix that only edits or swaps an argument of a call, like kafka-23538's
  inverted ternary, says which call's argument changed and how, instead of
  "other statements changed" (ticket #361; third expansion baseline, K2).

  Scenario: A changed argument is named with its call
    Given a base revision where method "validate" on class "Group" has the body "validateMember(memberId, \"offset-commit\");"
    And a head revision where "validate" on "Group" has the body "validateMember(memberId, \"txn-offset-commit\");"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Group#validate" is described as "Modify body of Group#validate: validateMember(…): \"offset-commit\" -> \"txn-offset-commit\""

  Scenario: Swapped branches of a conditional argument are named as a swap
    Given a base revision where method "validate" on class "Group" has the body "if (generationId >= 0) { validateMember(memberId, isTransactional ? \"offset-commit\" : \"txn-offset-commit\"); }"
    And a head revision where "validate" on "Group" has the body "if (generationId >= 0) { validateMember(memberId, isTransactional ? \"txn-offset-commit\" : \"offset-commit\"); }"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Group#validate" is described as "Modify body of Group#validate: swapped ?: branches in validateMember(…)"

  Scenario: Swapped branches of a returned conditional are named as a swap
    Given a base revision where static method "pick" on class "Strings" returns "index < 0 ? str : \"none\""
    And a head revision where static method "pick" on "Strings" returns "index < 0 ? \"none\" : str"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Strings#pick" is described as "Modify body of Strings#pick: swapped ?: branches in return"

  Scenario: A change to the number of arguments reads as changed arguments
    Given a base revision where method "log" on class "Audit" has the body "record(event);"
    And a head revision where "log" on "Audit" has the body "record(event, user);"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Audit#log" is described as "Modify body of Audit#log: arguments of record changed"

  Scenario: The same argument change on two calls is listed once with a count
    Given a base revision where method "log" on class "Audit" has the body "record(count); size = 1; record(count);"
    And a head revision where "log" on "Audit" has the body "record(count + 1); size = 1; record(count + 1);"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Audit#log" is described as "Modify body of Audit#log: record(…): count -> count + 1 ×2"

  Scenario: A statement that changed beyond a call's arguments is not named as one
    Given a base revision where method "log" on class "Audit" has the body "int count = record(event);"
    And a head revision where "log" on "Audit" has the body "long count = record(user);"
    When the semantic engine detects transformations between the revisions
    Then the body modification of "Audit#log" is described as "Modify body of Audit#log: other statements changed"
