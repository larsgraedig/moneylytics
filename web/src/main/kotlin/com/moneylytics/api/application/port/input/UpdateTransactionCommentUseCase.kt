package com.moneylytics.api.application.port.input

import com.moneylytics.api.domain.Transaction

interface UpdateTransactionCommentUseCase {
    fun updateComment(
        id: Long,
        userId: Long,
        comment: String?,
    ): Transaction?
}
