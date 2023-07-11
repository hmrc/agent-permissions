# agent-permissions

[![Build Status](https://travis-ci.org/hmrc/agent-permissions.svg)](https://travis-ci.org/hmrc/agent-permissions)

Backend service to store opt-in status and any agent-permissions-specific structures, such as groups.

Custom access groups, or just access groups are created via User level assignments in EACD.

Tax service access groups only exist within Agent Services as an allowlist of team members for a particular enrolment type. Only those currently supported by Agent Services Account are included. Income Record Viewer is not supported. Trusts are handled as one entity (taxable and non-taxable grouped together).

**Enrolments for tax service groups**
- HMRC-MTD-VAT
- HMRC-MTD-IT
- HMRC-TERS-ORG and HMRC-TERSNT-ORG (Trusts, HMRC-TERS)
- HMRC-CGT-PD
- HMRC-PPT-ORG

Even though custom access groups and tax service groups are separate, they cannot share the same name

Creating, updating and deleting custom access groups are intensive operations due to the need to reconcile with EACD.
Transactions can result in potential user level assignments (add clients OR team members) or de-assignments (remove clients OR team members)

## Endpoints

### Opt in/out

| **Method** | **Path**              | **Description**                           |Allows Assistant user|
|------------|-----------------------|-------------------------------------------|----|
| GET  | /arn/:arn/optin-status     | Gets the opt in status for an ARN including if any work items remain outstanding             | true |
| POST | /arn/:arn/optin            | Opt-in an agent to use agent permissions feature  | false |
| POST | /arn/:arn/optout           | Opt-out an agent from using agent permissions feature  | false |
| GET  | /arn/:arn/optin-record-exists   | Returns 204 if the ARN has opted-in otherwise returns 404  | true |

### Group checks
| **Method** | **Path**         | **Description**                           |Allows Assistant user|
|------------|------------------|-------------------------------------------|----|
| GET   | /arn/:arn/access-group-name-check?name=:encodedName      | Checks if group name has already been used. Returns OK or CONFLICT  | false |
| GET   | /arn/:arn/all-groups  |  Gets summaries of custom groups & tax service groups   | true |

### Create & Manage custom access groups
| **Method** | **Path**          | **Description**                           |Allows Assistant user|
|------------|-------------------|-------------------------------------------|----|
| POST   | /arn/:arn/groups      | Creates a custom access group. Returns CREATED          | false |
| GET    | /arn/:arn/groups      | Gets summaries of custom groups ONLY   | true |
| GET    | /groups/:groupId      | Deprecated for 1000+ get summary and paginated list instead. Gets custom access group based on groupId               | true |
| GET    | /custom-group/:groupId  | Gets custom group summary based on groupId                | true |
| PATCH  | /groups/:groupId      | Updates a group (name, clients, team members) from their groupId             | false |
| DELETE | /groups/:groupId      | Deletes a group from their groupId             | false |
| GET    | /group/:groupId/clients  | Gets paginated list of clients from group, can search/filter   | true |
|
### Create & Manage tax service groups
| **Method** | **Path**          | **Description**                           |Allows Assistant user|
|------------|-------------------|-------------------------------------------|----|
| POST   | /arn/:arn/tax-group   |  Creates a tax service group. Returns CREATED          | false |
| GET    | /arn/:arn/tax-groups  |  Gets summaries of tax service groups ONLY            | true |
| GET    | /arn/:arn/tax-group/:service  | Gets tax service group for ARN based on service             | true |
| GET    | /tax-group/:groupId   |  Gets tax service group based on groupId        | true |
| PATCH  | /tax-group/:groupId   | Updates a group (name, team members, excluded clients, auto-updates) from their groupId             | false |
| DELETE | /tax-group/:groupId   | Deletes a group from their groupId             | false |

### Manage clients/team members
| **Method** | **Path**               | **Description**         |Allows Assistant user|
|------------|------------------------|-------------------------|---------------------|
| GET   | /arn/:arn/client/:enrolmentKey/groups  |   Gets all group summaries that contain a given client   | false |
| GET   | /arn/:arn/team-member/:userId/groups   |   Gets all group summaries that contain a given team member  | true |
| GET   | /arn/:arn/unassigned-clients           |   Gets all clients not assigned to any access group, can paginate, search and filter | true |

### Other
| **Method** | **Path**                       | **Description**                           |Allows Assistant user|
|------------|--------------------------------|-------------------------------------------|----|
| GET   | /arn-allowed |   Allowlist to support private beta  | true |
| GET   | /private-beta-invite   | Check to see if they're in private beta or want to hide the invite banner, returns 200 if should be hidden  | true |
| POST  | /private-beta-invite/decline   | Agent opts out of seeing private beta invite banner | true |

## Running the tests

    sbt "test;IntegrationTest/test"

## Running the tests with coverage

    sbt "clean;coverageOn;test;IntegrationTest/test;coverageReport"

## Running the app locally
    sm --start AGENT_ALL -r
    sm --stop AGENT_PERMISSIONS
    sbt run

It should then be listening on port 9447

### License
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
