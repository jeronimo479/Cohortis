package com.example.cohortis

import java.util.UUID

data class Party(
    val id: UUID = UUID.randomUUID(),
    var name: String = "",
    val members: MutableList<Member> = mutableListOf(),
    var isActive: Boolean = false,
    var isPriority: Boolean = false
)
