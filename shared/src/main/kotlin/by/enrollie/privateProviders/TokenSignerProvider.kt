/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 7/15/22, 2:27 AM
 */

package by.enrollie.privateProviders

import by.enrollie.data_classes.User
import by.enrollie.data_classes.UserID
import by.enrollie.exceptions.UserDoesNotExistException

interface TokenSignerProvider {
    /**
     * Signs the token with the given user id.
     */
    fun signToken(user: User, token: String): String

    /**
     * Finds the user by ID and signs the token with it.
     * @throws UserDoesNotExistException if user with given id is not found.
     */
    fun signToken(userID: UserID, token: String): String

    /**
     * Verifies the token and returns the user associated with it (or null, if token is invalid).
     * @throws UserDoesNotExistException if user with given id is not found.
     */
    fun getSignedTokenUser(token: String): User?

    /**
     * Verifies the token and returns whether it is valid or not.
     */
    fun verifySignedToken(token: String): Boolean
}
