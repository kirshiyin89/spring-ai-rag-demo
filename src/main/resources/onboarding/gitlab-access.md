# GitLab Access

## Overview

FinFlow uses GitLab for source code, code review, CI/CD pipelines, and engineering documentation.

GitLab access depends on the employee's role.

## New Developers

The manager or team lead requests GitLab access during onboarding.

See `new-employee-onboarding.md`.

The request should include:

- employee name
- department
- team
- manager
- required GitLab group
- required role

## Access Levels

GitLab permissions follow the principle of least privilege.

Typical roles include:

- Guest
- Reporter
- Developer
- Maintainer

Employees should receive the lowest role that allows them to perform their work.

Most software developers receive the **Developer** role for their team's projects.

Maintainer access requires additional justification.

## Project Access

Employees do not automatically receive access to every GitLab project.

Access to another team's project requires a valid business reason and appropriate approval.

Do not share your GitLab credentials with another employee.

## CI/CD

GitLab CI/CD can deploy applications to company environments.

Production deployment permissions are restricted.

Developers may have permission to trigger normal deployment pipelines without having direct production administration access.

## Personal Access Tokens

Employees should use personal access tokens only when required.

Tokens must have the smallest required scope and lifetime.

Never commit a GitLab token to source code.

If a token is accidentally exposed, contact IT Security immediately.

## Access Problems

For normal GitLab access problems, contact the IT Service Desk.

Examples:

- cannot log in
- MFA problem
- missing group access
- missing project access
- expired account

For requests for new project access, use the normal access-request process through your manager.

## Leaving the Company

IT disables GitLab access when an employee leaves the company.

Access must not be transferred to another employee.