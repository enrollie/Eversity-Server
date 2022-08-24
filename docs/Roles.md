# Role system in Eversity

## Preamble

Permissions to do certain actions are defined by roles. Roles may be granted automatically by server while importing
data from Schools.by (for example, `CLASS.Student` role is automatically granted to every student in a class) or
manually by other users (i.e. `CLASS.AbsenceProvider` role may be granted to a student in class so that given student
may send absence information on behalf of class).

## Data that role contains

Role contains next data:

- `role` - ID of role (human-readable role name).
- `uniqueId` - Unique ID of role (UUID).
- `userId` - ID of user that has that role.
- `additionalInformation` - Map of type <Key=String, Value=String> with role-specific additional data (for example,
  `CLASS.Student` role contains `classId` - ID of class that student is in).
- `roleGrantedDateTime` - Date and time when role was granted.
- `roleRevokedDateTime` - Date and time when role was revoked (may be null in cases when role is not revoked).

## Roles

### `CLASS.AbsenceProvider`

This role allows user to send absence information on behalf of class. Server does not grant this role automatically.
This role may be granted manually by any user that has role `CLASS.ClassTeacher` or `SCHOOL.Administration`.

#### Defined additional information

| ID            | Optional? | Description                          |
|---------------|-----------|--------------------------------------|
| `classID`     | `false`   | ID of class that role was granted in |
| `delegatedBy` | `false`   | ID of user that granted given role.  |

#### Permissions granted to role

- `read_absence` on class with ID `classID`
- `edit_absence` on class with ID `classID`
- `read_lessons` on class with ID `classID`
- `read_students` on class with ID `classID`

### `CLASS.Student`

This role is granted automatically by server when student is added to class. This role can't be granted manually.

#### Defined additional information

| ID          | Optional? | Description                                          |
|-------------|-----------|------------------------------------------------------|
| `classID`   | `false`   | ID of class that role was granted in                 |
| `subgroups` | `true`    | List of subgroups IDs in which the student is listed |

#### Permissions granted to role

- `read_lessons` on class with ID `classID`
- `read_students` on class with ID `classID`

### `CLASS.ClassTeacher`

This role is granted automatically by server when class teacher is added to a class. This role can't be granted
manually.

#### Defined additional information

| ID          | Optional? | Description                                          |
|-------------|-----------|------------------------------------------------------|
| `classID`   | `false`   | ID of class that role was granted in                 |

#### Permissions granted to role

- `read_lessons` on class with ID `classID`
- `read_students` on class with ID `classID`
- `read_absence` on class with ID `classID`
- `edit_absence` on class with ID `classID`
- `request_sync` on class with ID `classID`

### `CLASS.Teacher`

This role is granted automatically by server when teacher is added to a class. This role can't be granted manually.

#### Defined additional information

| ID          | Optional? | Description                               |
|-------------|-----------|-------------------------------------------|
| `classID`   | `false`   | ID of class that role was granted in      |

### `SCHOOL.Administration`

This role is granted automatically by server when it registers any user with Schools.by user type `administration`
or `director`. This role can't be granted manually.

#### Defined additional information

This role has no additional information.

#### Permissions granted to role

- `read_lessons` on any class
- `read_students` on any class
- `read_absence` on any class
- `edit_absence` on any class
- `request_sync` on any class
- `read_statistics` on school

### `SCHOOL.SocialTeacher`

This role is granted manually by any user with role `SCHOOL.Administration`. Server does not grant this role
automatically.

#### Defined additional information

This role has no additional information.

#### Permissions granted to role

- `read_lessons` on any class
- `read_students` on any class
- `read_absence` on any class
- `edit_absence` on any class
- `request_sync` on any class
- `read_statistics` on school

### `SERVICE.SystemAdministrator`

This is a technical role that allows user to perform any actions on the server. This role can't be granted manually.

#### Defined additional information

This role has no additional information.

#### Permissions granted to role

Every permission in service is granted to this role.
