Feature: Athena visual identity — logo, favicon, and unified loading indicator
  Establishing Athena's visual identity (ticket #109) gives the product a
  recognizable, consistent character instead of a generic, unbranded shell:
  the Athena logo is visible wherever a user would look for it (the app
  itself, the browser tab, the README), and every wait in the app uses the
  same loading indicator rather than a different one per page.

  Scenario: The Athena logo appears in the application shell
    Given a user opens the Athena web application
    Then the Athena logo is visible in the application's header

  Scenario: The Athena logo appears as the browser tab's favicon
    Given a user opens the Athena web application
    Then the browser tab shows the Athena logo as its favicon

  Scenario: Every page uses the same loading indicator while waiting
    Given a user triggers an action that loads data
    When the data has not finished loading yet
    Then the user sees Athena's unified loading indicator, the same one used everywhere else in the application

  Scenario: The README shows the Athena logo at the top
    Given a visitor opens the project's README
    Then the Athena logo appears near the top of the README

  Scenario: The README's original artwork is preserved, not deleted
    Given a visitor opens the project's README
    Then the original Athena artwork image is still present, in an "Artwork" section near the end of the README
