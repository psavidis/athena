Feature: GitHub Access settings page
  GitHub access configuration lives in its own dedicated page (ticket
  #113/#145), separate from the main repository/PR browsing flow. The
  main page no longer needs a blocking full-page "Connect to GitHub" gate
  before it can be used, and access-related errors surface on the GitHub
  Access page rather than interrupting the main experience.

  Scenario: A connected user sees their connection status and accessible repositories
    Given the user is connected to GitHub as "octocat" with access to "octocat/hello-world"
    When the user opens the GitHub Access page
    Then the page shows the connection status as connected
    And the page shows the connected account "octocat"
    And the page shows "octocat/hello-world" in the accessible repositories

  Scenario: A not-yet-connected user sees a way to connect
    Given the user is not connected to GitHub
    When the user opens the GitHub Access page
    Then the page shows the connection status as not connected
    And the page shows a way to connect to GitHub

  Scenario: The GitHub Access page links to GitHub's own installation settings
    Given the user is connected to GitHub as "octocat"
    When the user opens the GitHub Access page
    Then the page shows a link to GitHub's installation access settings

  Scenario: The main page is usable without a blocking connect gate
    Given the user is not connected to GitHub
    When the user opens the main page
    Then the main page shows a prompt to connect, not a full-page takeover
    And the prompt links to the GitHub Access page

  Scenario: A GitHub-access error is shown on the GitHub Access page, not the main flow
    Given the user is connected to GitHub but the repository list request fails
    When the user opens the GitHub Access page
    Then the page shows a GitHub-access error
    And the main repository browsing flow is not interrupted by this error

  Scenario: The main page links to the GitHub Access page
    Given the user is connected to GitHub as "octocat"
    When the user opens the main page
    Then the main page shows a way to reach the GitHub Access page
