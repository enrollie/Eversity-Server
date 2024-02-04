# Environment variables recognized by Eversity Server

| Variable Name                               | Required?                                        | Acceptable Values     | Default value                                    | Description                                                                                                                                                          |
|---------------------------------------------|--------------------------------------------------|-----------------------|--------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `EVERSITY_ENV`                              | No                                               | `PROD`, `DEV`, `TEST` | `DEV`                                            | Points out in which environment Eversity Server is running. May have impact on logging depth or performance.                                                         |
| `EVERSITY_SERVER_NAME`                      | No                                               | Any string            | Randomly generated name based on server hardware | Defines human-friendly name of the server (such as "cold-penguin-2314") to identify it among others.                                                                 |
| `EVERSITY_LOAD_EVERY_PLUGIN`                | No                                               | `true`, `false`       | `false`                                          | Defines, whether Eversity Server should load every plugin regardless of its API version. Useful in DEV environment.                                                  |
| `EVERSITY_STRESS_TEST_MODE`                 | No                                               | `true`, `false`       | `false`                                          | Defines, whether Eversity Server is stress-tested. Stress test mode is intended to test DB and other functions of server without server making external connections. |
| `SENTRY_DSN`                                | Yes, if you want to use Sentry exception logging | Valid Sentry DSN      | None.                                            | Defines Sentry DSN to use. Do not set it if you do not plan on using Sentry logging.                                                                                 |
| `EVERSITY_DO_NOT_SEND_EXCEPTIONS_TO_SENTRY` | No                                               | `true`, `false`       | `false`                                          | If set to true, all occurred exceptions will be logged in terminal and log file, but won't be sent to Sentry.                                                        |
| `DATADOG_API_KEY`                           | Yes, if you want to use Datadog Metrics logging  | Valid Datadog API key | None.                                            | Defines Datadog API key to use. Do not set it if you do not plan on using it.                                                                                        |
| `DATADOG_API_URL`                           | Yes, if `DATADOG_API_KEY` is set                 | Valid Datadog API URL | None.                                            | Defines Datadog API URL to use.                                                                                                                                      |

Note: this table does not include environment variables that may be required by plugins. Please refer to plugin
documentation for more information.