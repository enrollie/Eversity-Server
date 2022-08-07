#
#  Copyright © 2021 - 2022
#  Author: Pavel Matusevich
#  Licensed under GNU AGPLv3
#  All rights are reserved.
#  Last updated: 6/26/22, 11:35 PM
#
actor User {
    permissions = ["read_lessons", "read_roles", "read"];
}

# Notes on working with Oso Polar definitions:
# 1. To get a property of Java/Kotlin class, use method `get*` instead of just using `.` (i.e. `lesson.getClassID()` instead of `lesson.classID`)
# 2. Use RolesProvider constant to get user roles (it provides a list of roles with all needed optimizations)

resource School{
    permissions = ["read_statistics", "edit_absence"];
    roles = ["SCHOOL.Administrator", "SCHOOL.SocialTeacher"];

    "read_statistics" if "SCHOOL.Administrator";
    "read_statistics" if "SCHOOL.SocialTeacher";
    "edit_absence" if "read_statistics"; # May be changed in the future
}

resource SchoolClass{
    permissions = ["read_absence", "edit_absence", "read_students", "request_sync", "read_lessons", "read"];
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
    "request_sync" if "SCHOOL.Administrator" on "school";
    "request_sync" if "SCHOOL.SocialTeacher" on "school";
}

resource Lesson{
    permissions = [];
    roles = ["LESSON.Teacher"];
    relations = {class: SchoolClass};
}

has_relation(school: School, "school", _: SchoolClass) if
    school = school;

has_role(user: User, name: String, _: School) if
    role in RolesProvider.roles(user) and
    role.getRole().getID() = name;

has_role(user: User, name: String, class: SchoolClass) if
    role in RolesProvider.rolesInClass(user, class) and
    role.getRole().getID() = name;

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

allow(user: User, "read_lessons", target: User) if
    (user.getId() = target.getId()) or
    (role in RolesProvider.roles(user) and role.getRole().getID().startsWith("SCHOOL")); # Users with any school-wide role `can read any user's lessons

allow(user: User, "read_roles", target: User) if
    (user.getId() = target.getId()) or
    (role in RolesProvider.roles(user) and role.getRole().getID().startsWith("SCHOOL")); # Users with any school-wide role can read any user's roles

allow(user: User, "read_all_roles", _: Unit) if
    role in RolesProvider.roles(user) and
    role.getRole().getID().startsWith("SCHOOL"); # Users with any school-wide role can read all roles

allow(_: User, "read", _: User);

allow(user: User, action: String, class: SchoolClass) if
    has_permission(user, action, class);

allow(user: User, action: String, school: School) if
    has_permission(user, action, school);

