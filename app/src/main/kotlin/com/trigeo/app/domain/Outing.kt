package com.trigeo.app.domain

import java.time.Instant
import java.util.UUID

data class Outing(
    val id: UUID,
    val name: String?,
    val createdAt: Instant,
) {
    val displayName: String
        get() = name?.takeIf { it.isNotBlank() } ?: "Untitled outing"
}
