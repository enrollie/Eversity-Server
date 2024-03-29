# Logging user in

## Preamble

At the moment, Eversity runs off of data from the [Schools.by](https://schools.by/) and also delegates user
authentication to it.

This is done to make it easier for server to manage users credentials (see this
cool [video](https://www.youtube.com/watch?v=8ZtInClXe1Q) Tom Scott did on a Computerphile channel. TL;DR the only
non-leakable password database is the one that does not exist. Eversity does that - it simply does not deal with
passwords and logins. It only stores cookies to Schools.by sessions and user IDs) and for schools to adopt this service
easily - they do not
need any effort to add their students and teachers data to the service - service simply clones data from already
existing information on Schools.by for them.

## Authentication

If you are developing an application that directly contacts with Eversity (i.e. client for Everisty) - please, state
several times that users are to enter their Schools.by credentials - they do not have any "Eversity credentials".

Get user's credentials and POST them to the `user/login` endpoint.

Example:

```bash
curl -X POST \
  -d '{"username": "totally_real_username", "password": "totally_real_password"}' \
  'http://localhost:8080/user/login'
```

After that there are two possibilities:

1. User is already registered. In this case, server will return HTTP 200 code with body containing authentication token
   and user ID.
2. User is not registered. In this case, server will return HTTP 202 code with a `Location` header specified. That
   location header contains a WebSocket endpoint address that should be used to let user observe registration process
   and for client to get login token (see [Live registration documentation](Live-registration.md)).

After getting authentication token, client must supply it with every request using HTTP Authentication header with
Bearer schema. Authentication token is a string that is generated by Eversity server and is valid for an unlimited
time (unless user explicitly logs out, in that case token is invalidated at the logout moment).
