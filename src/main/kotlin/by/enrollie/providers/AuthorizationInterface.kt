/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under GNU AGPLv3
 * All rights are reserved.
 * Last updated: 8/12/22, 3:18 AM
 */

package by.enrollie.providers

import com.osohq.oso.Exceptions.ForbiddenException
import com.osohq.oso.Exceptions.NotFoundException

interface AuthorizationInterface {
    /**
     * @throws ForbiddenException if actor may read the resource, but can't do particular action
     * @throws NotFoundException if actor may not read the resource
     * @return Nothing, if actor is authorized to do the action
     */
    fun authorize(actor: Any, action: String, resource: Any)

    /**
     * Filters out unauthorized recources from the list
     */
    fun <T> filterAllowed(actor: Any, action: String, resources: List<T>): List<T>
}
