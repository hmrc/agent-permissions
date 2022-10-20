
# agent-permissions

Backend service to store opt-in status and any agent-permissions-specific structures, such as groups.

## Endpoints

### Opt in/out

| **Method** | **Path**                       | **Description**                           |Allows Assistant user|
|------------|--------------------------------|-------------------------------------------|----|
| GET   | /arn/:arn/optin-status     | Gets the opt in status for an ARN including if any work items remain outstanding             | true |
| POST  | /arn/:arn/optin            | Opt-in an agent to use agent permissions feature  | false |
| POST  | /arn/:arn/optout           | Opt-out an agent from using agent permissions feature  | false |
| GET  | /arn/:arn/optin-record-exists           | Returns 204 if the ARN has opted-in otherwise returns 404  | true |

### Create or Manage access group
| **Method** | **Path**                       | **Description**                           |Allows Assistant user|
|------------|--------------------------------|-------------------------------------------|----|
| GET   | /arn/:arn/access-group-name-check?name=:encodedName      |    Checks if group name has already been used. Returns OK or CONFLICT  | false |
| POST  | /arn/:arn/groups             | Creates an access group. Returns CREATED          | false |
| GET   |  /arn/:arn/groups            | Gets summary of groups and unassigned clients     | true |
| GET   | /groups/:groupId            |  Gets access group based on groupId                | true |
| PATCH | /groups/:groupId             |  Updates a group (name, clients, team members) from their groupId             | false |
| DELETE | /groups/:groupId             |  Deletes a group from their groupId             | false |

### Manage clients/team members
| **Method** | **Path**                       | **Description**                           |Allows Assistant user|
|------------|--------------------------------|-------------------------------------------|----|
| GET   | /arn/:arn/client/:enrolmentKey/groups   |   Gets group summaries that contain a given client   | false |
| GET   | /arn/:arn/team-member/:userId/groups   |   Gets group summaries that contain a given team member  | true |

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

    sm --stop AGENT_PERMISSIONS
    sbt run

It should then be listening on port 9447

### License
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
