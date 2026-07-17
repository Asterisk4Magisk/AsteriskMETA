package com.github.kr328.clash.core.model

import kotlinx.serialization.Serializable

@Serializable
data class FetchOptions(
    val force: Boolean = false,
    val userAgent: String = "",
    val proxy: FetchProxy? = null,
)

@Serializable
data class FetchProxy(
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
)
