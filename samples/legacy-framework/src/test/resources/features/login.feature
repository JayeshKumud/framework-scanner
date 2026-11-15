Feature: Login

  Scenario: Log in with a valid user
    Given user is on login page
    When user logs in with "ada" and "secret"
    Then dashboard is shown
    And the welcome message shows "Ada"

  Scenario: Sign in, written by another team
    Given user is on login page
    When I sign in as "grace" with password "secret"
    Then the welcome message shows "Grace"
