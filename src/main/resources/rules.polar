#
#  Copyright © 2021 - 2022
#  Author: Pavel Matusevich
#  Licensed under GNU AGPLv3
#  All rights are reserved.
#  Last updated: 6/26/22, 11:35 PM
#
actor User {
    permissions = ["read_lessons"];
}

# Notes on working with Oso Polar definitions:
# 1. To get a property of Java/Kotlin class, use method `get*` instead of just using `.` (i.e. `lesson.getClassID()` instead of `lesson.classID`)
# 2. Use RolesProvider constant to get user roles (it provides a list of roles with all needed optimizations)

resource School{
    permissions = ["read_statistics", "edit_absence"];
    roles = ["SCHOOL.Principal", "SCHOOL.SocialTeacher", "SCHOOL.VicePrincipal"];

    "read_statistics" if "SCHOOL.Principal";
    "read_statistics" if "SCHOOL.SocialTeacher";
    "read_statistics" if "SCHOOL.VicePrincipal";
    "edit_absence" if "read_statistics"; # May be changed in the future
}

resource Class{
    permissions = ["read_absence", "edit_absence", "read_students", "request_sync", "read_lessons"];
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
    "request_sync" if "SCHOOL.Principal" on "school";
    "request_sync" if "SCHOOL.VicePrincipal" on "school";
    "request_sync" if "SCHOOL.SocialTeacher" on "school";
}

resource Lesson{
    permissions = [];
    roles = ["LESSON.Teacher"];
    relations = {class: Class};
}

has_relation(school: School, "school", _: Class) if
    school = school;

has_role(user: User, name: String, _: School) if
    role in RolesProvider.roles(user) and
    role.getRoleID() = name;

has_role(user: User, name: String, class: Class) if
    role in RolesProvider.rolesInClass(user, class) and
    role.getRoleID() = name;

has_permission(user: User, "read_absence", class: Class) if
    lesson in RolesProvider.lessonRoles(user) and
    lesson.getClassID() = class.getId();

has_permission(user: User, "edit_absence", class: Class) if
    has_permission(user, "read_absence", class) and
    lesson in RolesProvider.lessonRoles(user) and
    TimeValidator.isCurrentLesson(lesson, class);

has_permission(_: User, "read", _: Class);

has_relation(class: Class, "class", lesson: Lesson) if
    lesson.classID = class.ID;

allow(user: User, "read_lessons", target: User) if
    (user.getId() = target.getId()) or
    (role in RolesProvider.roles(user) and role.getRoleID().startsWith("SCHOOL")); # Users with school-wide roles can read any user's lessons

allow(user: User, action: String, class: Class) if
    has_permission(user, action, class);

allow(user: User, action: String, school: School) if
    has_permission(user, action, school);

