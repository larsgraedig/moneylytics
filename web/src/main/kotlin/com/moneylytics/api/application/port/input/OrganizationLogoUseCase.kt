package com.moneylytics.api.application.port.input

interface OrganizationLogoUseCase {
    fun uploadLogo(
        orgId: Long,
        logoData: ByteArray,
        contentType: String,
        requestingUserId: Long,
    )

    fun deleteLogo(
        orgId: Long,
        requestingUserId: Long,
    )

    fun getLogo(orgId: Long): Pair<ByteArray, String>?
}
