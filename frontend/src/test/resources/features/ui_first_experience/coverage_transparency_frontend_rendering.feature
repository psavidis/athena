Feature: Coverage transparency in the Semantic Canvas and Explorer (frontend)
  When Athena's analysis leaves changed files unrepresented, the reviewer
  sees that plainly and can open the raw diff of each such file, so an
  incomplete review never looks complete (ticket #261).

  Scenario: The Canvas warns when some changed files are not represented
    Given the reviewer has opened a review where 3 of 8 changed files are not represented by any Change
    When the reviewer views the Semantic Canvas
    Then a coverage indicator says 3 of 8 changed files are not represented

  Scenario: The Explorer shows the same coverage indicator
    Given the reviewer has opened a review where 3 of 8 changed files are not represented by any Change
    When the reviewer views the Semantic Change Explorer
    Then a coverage indicator says 3 of 8 changed files are not represented

  Scenario: The coverage indicator lists the unrepresented files with their reasons
    Given the reviewer has opened a review where "pom.xml" is unrepresented as an unsupported file type and "Fraction.java" is unrepresented because no semantic change was detected
    When the reviewer opens the coverage indicator
    Then "pom.xml" is listed with reason "unsupported file type"
    And "Fraction.java" is listed with reason "no semantic change detected"

  Scenario: Choosing an unrepresented file opens its raw diff
    Given the reviewer has opened the list of unrepresented files
    When the reviewer chooses "Fraction.java"
    Then the Diff view shows the raw diff of "Fraction.java"

  Scenario: No indicator is shown when every changed file is represented
    Given the reviewer has opened a review where every changed file is represented by a Change
    When the reviewer views the Semantic Canvas
    Then no coverage indicator is shown

  Scenario: A review with no Changes says so and lists the changed files
    Given the reviewer has opened a review with 5 changed files and no detected Changes
    When the reviewer views the Semantic Canvas
    Then the Canvas states that no semantic changes were detected in 5 changed files
    And the 5 changed files are listed, each openable as a raw diff
