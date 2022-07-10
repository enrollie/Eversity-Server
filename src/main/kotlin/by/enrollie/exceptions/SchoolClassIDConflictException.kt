/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/10/22, 11:16 PM
 */

package by.enrollie.exceptions

import by.enrollie.data_classes.ClassID

class SchoolClassIDConflictException(classID: ClassID) : Exception("Class ID $classID already exists")
