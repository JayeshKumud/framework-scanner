Feature: Users API

  Scenario: Read a user
    When I request user "42"
    Then the user name is "Ada"
