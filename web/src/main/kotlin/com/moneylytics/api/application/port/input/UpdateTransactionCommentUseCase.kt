package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

interface UpdateTransactionCommentUseCase {
    fun updateComment(
        id: Long,
        organizationId: Long,
        comment: String?,
    ): Transaction?
}
