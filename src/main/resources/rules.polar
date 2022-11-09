#
#  Copyright © 2021 - 2022
#  Author: Pavel Matusevich
#  Licensed under GNU AGPLv3
#  All rights are reserved.
#  Last updated: 6/26/22, 11:35 PM
#
actor User {
    permissions = ["read_lessons", "read_roles", "read", "edit_roles"];
}

# Notes on working with Oso Polar definitions:
# 1. To get a property of Java/Kotlin class, use method `get*` instead of just using `.` (i.e. `lesson.getClassID()` instead of `lesson.classID`)
# 2. Use RolesProvider constant to get user roles (it provides a list of roles with all needed optimizations)

resource School{
    permissions = ["read_statistics", "edit_absence", "read_all_absences", "edit_roles"];
    roles = ["SCHOOL.Administration", "SCHOOL.SocialTeacher"];

    "read_all_absences" if "SCHOOL.Administration";
    "read_all_absences" if "SCHOOL.SocialTeacher";
    "read_statistics" if "read_all_absences";
    "edit_absence" if "read_statistics"; # May be changed in the future
    "edit_roles" if "SCHOOL.Administration";
}

resource SchoolClass{
    permissions = ["read_absence", "edit_absence", "read_students", "request_sync", "read_lessons", "read", "edit_roles"];
    roles = ["CLASS.Student", "CLASS.ClassTeacher", "CLASS.AbsenceProvider"];
    relations = {school: School};

    "read_students" if "read_absence";
    "read_students" if "CLASS.Student";
    "read_lessons" if "read_students";
    "read_absence" if "read_statistics" on "school";
    "read_absence" if "CLASS.ClassTeacher";
    "read_absence" if "CLASS.AbsenceProvider";
    "edit_absence" if "CLASS.ClassTeacher";
    "edit_absence" if "CLASS.AbsenceProvider";
    "edit_absence" if "edit_absence" on "school";
    "request_sync" if "CLASS.ClassTeacher";
    "request_sync" if "SCHOOL.Administration" on "school";
    "request_sync" if "SCHOOL.SocialTeacher" on "school";
    "edit_roles" if "request_sync";
}

resource Lesson{
    permissions = [];
    roles = [];
    relations = {class: SchoolClass};
}

has_role(user: User, name: String, _: School) if
    role in RolesProvider.roles(user) and
    role.getRole().getID() = name;

has_role(user: User, name: String, class: SchoolClass) if
    role in RolesProvider.rolesInClass(user, class) and
    role.getRole().getID() = name;

has_relation(School: School, "school", _: SchoolClass);

has_permission(user: User, "read_absence", class: SchoolClass) if
    lesson in LessonsProvider.getTodayLessons(user) and
    lesson.getClassID() = class.getId();

has_permission(user: User, "edit_absence", class: SchoolClass) if
    has_permission(user, "read_absence", class) and
    lesson in LessonsProvider.getTodayLessons(user) and
    lesson.getClassID() = class.getId() and
    TimeValidator.isCurrentLesson(lesson);

has_permission(_: User, "read", _: SchoolClass);

has_relation(class: SchoolClass, "class", lesson: Lesson) if
    lesson.classID = class.ID;

has_permission(user: User, "edit_roles", target: User) if
    user = target or
    (role in RolesProvider.roles(user) and role.getRole().getID().startsWith("SCHOOL")); # Users with any school-wide role `can edit roles

allow(user: User, "read_lessons", target: User) if
    (user.getId() = target.getId()) or
    (role in RolesProvider.roles(user) and role.getRole().getID().startsWith("SCHOOL")); # Users with any school-wide role `can read any user's lessons

allow(user: User, "read_roles", target: User) if
    (user.getId() = target.getId()) or
    (role in RolesProvider.roles(user) and role.getRole().getID().startsWith("SCHOOL")); # Users with any school-wide role can read any user's roles

allow(user: User, "read_all_roles", _: Unit) if
    role in RolesProvider.roles(user) and
    role.getRole().getID().startsWith("SCHOOL"); # Users with any school-wide role can read all roles

allow(user: User, "read_all_roles", _: School) if
    role in RolesProvider.roles(user) and
    role.getRole().getID().startsWith("SCHOOL"); # Users with any school-wide role can read all users

allow(_: User, "read", _: User);
allow(user: User, "edit_roles", target: User) if
    has_permission(user, "edit_roles", target);

allow(user: User, action: String, class: SchoolClass) if
    has_permission(user, action, class);

allow(user: User, action: String, school: School) if
    has_permission(user, action, school);

