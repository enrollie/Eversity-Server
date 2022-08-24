# Live registration observation

## Preamble

Eversity manages users registration by itself, so users do not need to register manually - they only have to log in, on
which Eversity will do all the data-getting by itself. However, due to slowness of data source that Eversity uses in
given instance, it is not always a fast process - getting data in the worst case may take up to 30 seconds. To make this
waiting less painful for the user to observe, Eversity provides a way to observe registration steps in real time. It is
done by Eversity broadcasting registration steps taken to the client using websockets.

## Begin observing registration

After an unregistered user tries to log into the system, Eversity server will return HTTP 303 code with a `Location`
header specified. To receive an authentication token and observer registration process, simply open a WS connection to
an endpoint specified in the `Location` header of login response.

## Message schema

Upon connecting to the WS endpoint, server will begin sending messages that are defined with this schema:

```json json_schema
{
  "type": "object",
  "properties": {
    "eventType": {
      "type": "string",
      "enum": ["INFORMATION", "FAILURE", "AUTHENTICATION", "NAME"]
    },
    "step": {
      "type": "integer"
    },
    "totalSteps": {
      "type": "integer",
      "description": "If Eversity total ammount of steps is indetermined, field will be equal to `-1`"
    },
    "message": {
      "type": "string",
      "description": "Message that should be presented to user"
    },
    "additionalData": {
      "type": "object",
      "description": "Map of type <Key=String, Value=String> with additional data based on event type"
    }
  }
}
```

Additional data that may be contained in corresponding field:

| Type             | Keys                                                                                                            |
|------------------|-----------------------------------------------------------------------------------------------------------------|
| `INFORMATION`    | None.                                                                                                           |
| `FAILURE`        | None.                                                                                                           |
| `AUTHENTICATION` | `userId` - ID of user (integer). `token` - authentication token (string).                                       |
| `NAME`           | `firstName` - First name (string); `middleName` - Middle name (string or null); `lastName` - Last name (string) |

## Closing connection

Server will close connection automatically after `FAILURE` or `AUTHENTICATION` message.
