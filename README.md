
# agent-permissions

Backend service to store opt-in status and any agent-permissions-specific structures, such as groups.

## Endpoints

### Opt in/out

| **Method** | **Path**                       | **Description**                           |
|------------|--------------------------------|-------------------------------------------|
| GET   | /arn/:arn/optin-status     | Gets the opt in status for an ARN including if any work items remain outstanding             |
| POST  | /arn/:arn/optin            | Opt-in an agent to use agent permissions feature  |
| POST  | /arn/:arn/optout           | Opt-out an agent from using agent permissions feature  |
| GET  | /arn/:arn/optin-record-exists           | Returns 204 if the ARN has opted-in otherwise returns 404  |

### Create or Manage access group
| **Method** | **Path**                       | **Description**                           |
|------------|--------------------------------|-------------------------------------------|
| GET   | /arn/:arn/access-group-name-check?name=:encodedName      |    Checks if group name has already been used. Returns OK or CONFLICT  |
| POST  | /arn/:arn/groups             | Creates an access group. Returns CREATED          |
| GET   |  /arn/:arn/groups            | Gets summary of groups and unassigned clients     |
| GET   | /groups/:groupId            |  Gets access group based on groupId                |
| PATCH | /groups/:groupId             |  Updates a group (name, clients, team members) from their groupId             |
| DELETE | /groups/:groupId             |  Deletes a group from their groupId             |

### Manage clients/team members
| **Method** | **Path**                       | **Description**                           |
|------------|--------------------------------|-------------------------------------------|
| GET   | /arn/:arn/client/:enrolmentKey/groups   |   Gets group summaries that contain a given client   |
| GET   | /arn/:arn/team-member/:userId/groups   |   Gets group summaries that contain a given team member  |


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
