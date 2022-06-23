
# agent-permissions

Backend service to store opt-in status and any agent-permissions-specific structures, such as groups.

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
