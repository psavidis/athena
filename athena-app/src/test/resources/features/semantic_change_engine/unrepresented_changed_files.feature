Feature: Changed files not represented by any Change
  Athena's semantic analysis does not model every kind of edit, so a
  reviewer must be able to see which changed files no Change covers, why,
  and read their raw diff, rather than mistaking a partial analysis for a
  complete one (ticket #260; epic #4 §44: raw diff access is never blocked).

  Scenario: A changed file with no detected Change is listed as unrepresented
    Given the user has created a standalone Diff where file "Fraction.java" changed but no Change was detected for it
    When the user requests the changed files not represented by any Change
    Then "Fraction.java" is listed as unrepresented with reason "no semantic change detected"
    And its listing shows whether it was added, modified or removed, and how many lines and hunks changed

  Scenario: A changed file in an unsupported language is listed as unrepresented
    Given the user has created a standalone Diff where only the build file "build.gradle" and one Java file changed
    And a Change was detected for the Java file
    When the user requests the changed files not represented by any Change
    Then "build.gradle" is listed as unrepresented with reason "unsupported file type"
    And the Java file is not listed

  Scenario: A changed file that could not be parsed is listed as unrepresented
    Given the user has created a standalone Diff where one changed Java file fails to parse
    When the user requests the changed files not represented by any Change
    Then that file is listed as unrepresented with reason "could not be parsed"

  Scenario: The listing reports how many changed files are represented
    Given the user has created a standalone Diff where 3 files changed and Changes were detected for 2 of them
    When the user requests the changed files not represented by any Change
    Then the listing reports 3 changed files, of which 2 are represented

  Scenario: A file whose content is identical in both revisions is never listed
    Given the user has created a standalone Diff where file "Unchanged.java" is identical in both revisions
    When the user requests the changed files not represented by any Change
    Then "Unchanged.java" is not listed

  Scenario: Every changed file is represented
    Given the user has created a standalone Diff where every changed file has at least one Change
    When the user requests the changed files not represented by any Change
    Then no file is listed as unrepresented

  Scenario: The raw diff of an unrepresented file can be read
    Given the user has created a standalone Diff where file "Fraction.java" changed but no Change was detected for it
    When the user requests the raw diff of "Fraction.java"
    Then the unified diff of "Fraction.java" between the two revisions is returned

  Scenario: The raw diff of a file that is not part of the change is refused
    Given the user has created a standalone Diff where file "Unchanged.java" is identical in both revisions
    When the user requests the raw diff of "Unchanged.java"
    Then the request is rejected as not found

  Scenario: The unrepresented-files listing works for a standalone Diff without a GitHub connection
    Given the user is not connected to GitHub
    And the user has created a standalone Diff where file "Fraction.java" changed but no Change was detected for it
    When the user requests the changed files not represented by any Change
    Then "Fraction.java" is listed as unrepresented with reason "no semantic change detected"

  Scenario: The unrepresented-files listing is unavailable when nothing is selected
    Given the user has not selected a PR or created a Diff
    When the user requests the changed files not represented by any Change
    Then the request is rejected because nothing is selected
