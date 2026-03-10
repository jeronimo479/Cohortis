package com.example.cohortis2

import java.util.UUID

data class Party(
    val id: UUID = UUID.randomUUID(),
    var name: String = "",
    val members: MutableList<Member> = mutableListOf(),
    var isTemplate: Boolean = false,
    var isVisible: Boolean = true
)
