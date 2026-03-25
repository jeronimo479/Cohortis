package com.example.cohortis

import java.util.UUID

enum class PartyStatus {
    INACTIVE, ACTIVE, PRIMARY
}

data class Party(
    val id: UUID = UUID.randomUUID(),
    var name: String = "",
    val members: MutableList<Member> = mutableListOf(),
    var status: PartyStatus = PartyStatus.INACTIVE
)
