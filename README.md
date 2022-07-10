# Eversity Server

Server of Eversity Project. Uses [Schools.by](https://schools.by/) as main data source.

# Features

- Fast and reliable
- Easy to set up and maintain
- Does its job well

# Installation

## Requirements

- JRE 16+
- School that is connected to and using [Schools.by](https://schools.by/)
- Be sure to provide consistent internet connection (in case you set it up in school)
- Any reverse proxy (we recommend using [nginx](https://www.nginx.com/)) with valid SSL certificate

## Installation process

1. Clone the repository, CD into it and run `chmod +x gradlew && ./gradlew shadowJar`
2. Copy newly created JAR from `build/libs` to any location you desire
3. Get and set up any Eversity Database Provider you desire (there is a list of official providers down below)
4. Get and set up any Eversity Configuration Provider you desire
5. Run with `java -jar <path to JAR>`
6. You should be able to access Eversity Server at `http://localhost:8080`
7. Set up your reverse proxy to proxy Eversity Server and forbid any direct connection to Eversity Server from the
   internet

# License

```
Eversity Server is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

Eversity Server is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with Eversity Server.  If not, see <https://www.gnu.org/licenses/>
```

# Contributing

Contributions are welcome and encouraged. Contributions are accepted in the form of pull requests and/or issues.

# Support and Feedback

If you need help installing and/or using Eversity, contact [@Neitex](https://github.com/neitex) by using email provided
on the page. 
