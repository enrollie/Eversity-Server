# Data source communicator

The purpose of a data source communicator is to provide a common interface for any data source (currently, only
Schools.by is supported).
The communicator is responsible for fetching data from the data source and applying it to the database.

Each process of registering and/or updating user/class **must** have a unique UUID (required for logging and progress showing purposes).

## Registration of a user

The registration of a user is done by the data source communicator.
After beginning of a registration, the data source communicator is expected to do the following:

1. Check if the user is already registered in the database. If so, throw an exception (since application is expected to
   check if the user is already registered).
2. Fetch basic user data of the user and check provided credentials for validity.
3. Get approximate list of roles of the user. (i.e. if user has a "Director" user type in Schools.by, user is expected
   to have a `SCHOOL.Administration` role, if user has a "Pupil" user type, it is expected to have `CLASS.Student` role,
   etc.)
4. If role list is empty, reject registration with an appropriate error message.
   4.1. If user is expected to have `CLASS.Student` role, reject registration with an error message to contact the class
   teacher to update class's student list.
5. If user may have `CLASS.ClassTeacher` role (i.e. user has a "Teacher" user type in Schools.by), fetch list of classes
   of the user and check if the user is a class teacher of any classes.
   5.1. If so, register class (that procedure is described in the next section).
   5.2. If not, continue with an empty role list, but do not reject registration.
6. Obtain all information required to create all user roles (i.e. class).
7. Trigger database role update procedure (that procedure is described in the database architecture document).
8. Commit everything to the database **in a single transaction** and return a user auth token.

## Registration of a class

Registration of a class is done while registering a class teacher (user with `CLASS.ClassTeacher` role).

1. Fetch basic class data of the class (i.e. class name, class number, etc.).
2. Fetch list of students of the class.
3. Fetch list of lessons of the class. Data source communicator is expected to fetch all lessons of the class, even if
   they are not in the current quarter.
4. Register class in the database and students **in separate transactions**.
5. Register lessons in the database **in a single transaction**.
6. Continue with registration of a user.

After registration of a class, the data source communicator is expected create `CLASS.ClassTeacher` role for the user.

## Updating class

Updating class is almost the same as registration of a class, except that the data source communicator is expected to
only update students list (with creating or deleting students as needed) and lessons list (with creating or deleting
lessons as needed) and not to create a new class.

## Progress messages

The data source communicator is expected to send progress messages `DataSourceCommunicatorInterface::messagesBroadcast` while doing its job.
Messages are used to inform end user about the progress of the registration/update process.

Messages should have text on Russian language.

`Message::uuid` is expected to be the same as the UUID of the process.
