Feature: Athena recognizes simple refactorings end to end
  A developer applies one refactoring to a small repository of plain classes,
  and Athena, given the two commits, shows it the way a reviewer would describe
  it: one Change naming the refactoring, not the scattered line edits a plain
  diff shows (tickets #384–#387). Each scenario runs the whole product path:
  git checkout, analysis, and the Change Map and Explorer the web UI shows.

  Background:
    Given a repository whose classes "Account", "Bank" and "Audit" use each other

  # Ticket #384: a rename and its reference updates are one Change.

  Scenario: A field rename reads as one rename
    When the developer renames "balance" to "funds" in "Account"
    And the reviewer opens the Change Map of the two commits
    Then the Change Map lists exactly "Rename field Account#balance -> funds"
    And the Explorer shows no "Mechanical Replacement" entry

  Scenario: A method rename used from other files reads as one rename with its references
    When the developer renames "getBalance" to "currentFunds" in "Account", "Bank" and "Audit"
    And the reviewer opens the Change Map of the two commits
    Then the Change Map lists exactly "Rename Account#getBalance -> currentFunds (references updated in 2 files)"
    And the Explorer shows no "Mechanical Replacement" entry

  # Ticket #386: a local variable or parameter rename names its kind and scope.

  Scenario: A local variable rename names the variable's method
    When the developer renames "updated" to "newFunds" in "Account"
    And the reviewer opens the Change Map of the two commits
    Then the Change Map lists exactly "Rename local variable updated -> newFunds in Account#deposit"

  Scenario: A parameter rename names every method it's a parameter of
    When the developer renames "amount" to "value" in "Account"
    And the reviewer opens the Change Map of the two commits
    Then the Change Map lists exactly "Rename parameter amount -> value in Account#deposit, Account#canWithdraw"

  Scenario: An identifier that is neither a local variable nor a parameter keeps the plain rename title
    When the developer renames "balance" to "funds" in "Audit"
    And the reviewer opens the Change Map of the two commits
    Then the Change Map lists exactly "Rename balance -> funds"

  # Ticket #385: a class rename's ripple into other classes is reference updates, not API changes.

  Scenario: A class rename used by other classes reads as one rename with its references
    When the developer renames class "Account" to "Wallet"
    And the reviewer opens the Change Map of the two commits
    Then the Change Map lists exactly "Rename class Account -> Wallet (references updated in 2 files)"
    And the Explorer shows no "Change Signature" entry
    And the Explorer shows no "Change API Responsibility" entry

  # Ticket #387: members moved to another position in the same class, unchanged.

  Scenario: A method moved within its class reads as a reorder
    When the developer moves "canWithdraw" of "Account" before "getBalance"
    And the reviewer opens the Change Map of the two commits
    Then the Change Map lists exactly "Reorder members of Account: canWithdraw moved before getBalance"
    And no changed file is left unrepresented

  Scenario: Several members moved within their class read as one reorder
    When the developer moves "canWithdraw" of "Account" before "getBalance"
    And the developer moves "balance" of "Account" to the end
    And the reviewer opens the Change Map of the two commits
    Then the Change Map lists exactly "Reorder members of Account: 2 members moved"

  Scenario: A moved method that also changed is not a reorder
    When the developer moves "canWithdraw" of "Account" before "getBalance"
    And the developer renames "amount" to "limit" in "Account"
    And the reviewer opens the Change Map of the two commits
    Then the Change Map lists no reorder
