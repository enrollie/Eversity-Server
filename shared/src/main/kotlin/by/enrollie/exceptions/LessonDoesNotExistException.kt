/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/17/22, 10:22 PM
 */

package by.enrollie.exceptions

import by.enrollie.data_classes.LessonID

class LessonDoesNotExistException(lessonID: LessonID) : Exception("Lesson with ID $lessonID does not exist")
