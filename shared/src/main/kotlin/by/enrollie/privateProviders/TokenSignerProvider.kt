/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 2:27 AM
 */

package by.enrollie.privateProviders

import by.enrollie.data_classes.User

interface TokenSignerProvider {
    fun signToken(user: User, token: String): String
}
