package com.example.cohortis

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.UUID

/**
 * Data structure for importing/exporting the entire library.
 * Includes a version number for forward compatibility.
 */
data class LibraryExport(
    val version: Int = DataManager.CURRENT_SCHEMA_VERSION,
    val members: List<Member> = emptyList(),
    val parties: List<Party> = emptyList()
)

/**
 * Manages the persistence of application data using [SharedPreferences] and [Gson].
 * Uses custom deserializers to handle schema changes and maintain data integrity.
 */
class DataManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("CohortisPrefs", Context.MODE_PRIVATE)
    
    /**
     * Custom GSON instance configured with robust deserializers for schema evolution.
     */
    val gson: Gson = GsonBuilder()
        .registerTypeAdapter(Member::class.java, MemberDeserializer())
        .registerTypeAdapter(Party::class.java, PartyDeserializer())
        .registerTypeAdapter(PartyMember::class.java, PartyMemberDeserializer())
        .create()

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        private const val KEY_SCHEMA_VERSION = "schemaVersion"
        private const val KEY_MEMBER_LIBRARY = "memberLibrary"
        private const val KEY_PARTY_LIBRARY = "partyLibrary"
        private const val KEY_ACTIVE_PARTIES = "activeParties"
        private const val KEY_PRIORITY_ID = "priorityPartyId"
        private const val KEY_CURRENT_ROUND = "currentRound"
    }

    private var _memberLibrary: MutableList<Member>? = null
    private var _partyLibrary: MutableList<Party>? = null
    private var _activeParties: MutableList<Party>? = null

    init {
        checkAndMigrate()
    }

    /**
     * Checks the stored schema version and performs necessary migrations.
     * v1 -> v2: Extract full Member objects from parties and move them to the library template collection.
     */
    private fun checkAndMigrate() {
        val savedVersion = prefs.getInt(KEY_SCHEMA_VERSION, 1)
        if (savedVersion < CURRENT_SCHEMA_VERSION) {
            val oldPartiesJson = prefs.getString(KEY_PARTY_LIBRARY, null)
            val oldActiveJson = prefs.getString(KEY_ACTIVE_PARTIES, null)
            
            val extractedTemplates = mutableListOf<Member>()
            
            fun scanForTemplates(json: String?) {
                if (json.isNullOrBlank()) return
                try {
                    val element = JsonParser.parseString(json)
                    val partiesArray = when {
                        element.isJsonArray -> element.asJsonArray
                        element.isJsonObject && element.asJsonObject.has("parties") -> element.asJsonObject.getAsJsonArray("parties")
                        else -> return
                    }
                    
                    partiesArray.forEach { partyElement ->
                        if (!partyElement.isJsonObject) return@forEach
                        val membersArray = partyElement.asJsonObject.getAsJsonArray("members") ?: return@forEach
                        membersArray.forEach { memberElement ->
                            if (memberElement.isJsonObject) {
                                val mObj = memberElement.asJsonObject
                                // In v1, full Member had a name and stats. v2 PartyMember doesn't.
                                if (mObj.has("name") && (mObj.has("hitDice") || mObj.has("thac0") || mObj.has("hpFull"))) {
                                    try {
                                        val m = gson.fromJson(mObj, Member::class.java)
                                        if (m != null) extractedTemplates.add(m)
                                    } catch (e: Exception) {}
                                }
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
            
            scanForTemplates(oldPartiesJson)
            scanForTemplates(oldActiveJson)
            
            if (extractedTemplates.isNotEmpty()) {
                val currentLib = memberLibrary
                val migrationIdMap = mutableMapOf<UUID, UUID>()

                extractedTemplates.forEach { member ->
                    // Consolidate duplicates by name during migration
                    val existing = currentLib.find { it.id == member.id } 
                        ?: currentLib.find { it.name.equals(member.name, ignoreCase = true) }
                    
                    if (existing == null) {
                        currentLib.add(member)
                        migrationIdMap[member.id] = member.id
                    } else {
                        migrationIdMap[member.id] = existing.id
                    }
                }
                _memberLibrary = currentLib
                
                // Re-link party members to the consolidated template IDs
                fun reLink(list: MutableList<Party>) {
                    list.forEach { party ->
                        party.members.forEach { pm ->
                            migrationIdMap[pm.memberId]?.let { pm.memberId = it }
                        }
                    }
                }
                
                val parties = partyLibrary
                val active = activeParties
                reLink(parties)
                reLink(active)
                
                _partyLibrary = parties
                _activeParties = active
            }
            
            // Trigger a save to update the JSON structure and schema version
            saveAll(memberLibrary, partyLibrary, activeParties, priorityPartyId, currentRound)
        }
    }

    /** Global member library. Loads from disk on first access. */
    var memberLibrary: MutableList<Member>
        get() {
            if (_memberLibrary == null) {
                val json = prefs.getString(KEY_MEMBER_LIBRARY, null)
                _memberLibrary = try {
                    if (json.isNullOrBlank()) mutableListOf()
                    else gson.fromJson(json, object : TypeToken<MutableList<Member>>() {}.type) ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }
            }
            return _memberLibrary!!
        }
        set(value) {
            _memberLibrary = value
            prefs.edit().putString(KEY_MEMBER_LIBRARY, gson.toJson(value)).apply()
        }

    /** List of all parties. */
    var partyLibrary: MutableList<Party>
        get() {
            if (_partyLibrary == null) {
                val json = prefs.getString(KEY_PARTY_LIBRARY, null)
                _partyLibrary = try {
                    if (json.isNullOrBlank()) mutableListOf()
                    else gson.fromJson(json, object : TypeToken<MutableList<Party>>() {}.type) ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }
            }
            return _partyLibrary!!
        }
        set(value) {
            _partyLibrary = value
            prefs.edit().putString(KEY_PARTY_LIBRARY, gson.toJson(value)).apply()
        }

    /** Parties currently marked as active. */
    var activeParties: MutableList<Party>
        get() {
            if (_activeParties == null) {
                val json = prefs.getString(KEY_ACTIVE_PARTIES, null)
                _activeParties = try {
                    if (json.isNullOrBlank()) mutableListOf()
                    else gson.fromJson(json, object : TypeToken<MutableList<Party>>() {}.type) ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }
            }
            return _activeParties!!
        }
        set(value) {
            _activeParties = value
            prefs.edit().putString(KEY_ACTIVE_PARTIES, gson.toJson(value)).apply()
        }

    var priorityPartyId: UUID?
        get() {
            val idStr = prefs.getString(KEY_PRIORITY_ID, null)
            return try { if (!idStr.isNullOrBlank()) UUID.fromString(idStr) else null } catch (e: Exception) { null }
        }
        set(value) {
            prefs.edit().putString(KEY_PRIORITY_ID, value?.toString()).apply()
        }

    var currentRound: Int
        get() = prefs.getInt(KEY_CURRENT_ROUND, 0)
        set(value) {
            prefs.edit().putInt(KEY_CURRENT_ROUND, value).apply()
        }

    /** Persists all data and updates the schema version. */
    fun saveAll(members: List<Member>, parties: List<Party>, active: List<Party>, priorityId: UUID?, round: Int) {
        _memberLibrary = members.toMutableList()
        _partyLibrary = parties.toMutableList()
        _activeParties = active.toMutableList()
        
        prefs.edit().apply {
            putString(KEY_MEMBER_LIBRARY, gson.toJson(_memberLibrary))
            putString(KEY_PARTY_LIBRARY, gson.toJson(_partyLibrary))
            putString(KEY_ACTIVE_PARTIES, gson.toJson(_activeParties))
            putString(KEY_PRIORITY_ID, priorityId?.toString())
            putInt(KEY_CURRENT_ROUND, round)
            putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
        }.apply()
    }

    fun clearAll() {
        _memberLibrary = null
        _partyLibrary = null
        _activeParties = null
        prefs.edit().clear().apply()
    }

    private class MemberDeserializer : JsonDeserializer<Member> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Member {
            val obj = json.asJsonObject
            val name = obj.get("name")?.asString ?: ""
            val idStr = obj.get("id")?.asString
            val id = try {
                if (!idStr.isNullOrBlank()) UUID.fromString(idStr)
                else if (name.isNotBlank()) UUID.nameUUIDFromBytes(name.toByteArray())
                else UUID.randomUUID()
            } catch (e: Exception) {
                if (name.isNotBlank()) UUID.nameUUIDFromBytes(name.toByteArray())
                else UUID.randomUUID()
            }

            return Member(
                id = id,
                name = name,
                isPC = obj.get("isPC")?.asBoolean ?: false,
                classLevels = obj.get("classLevels")?.asString ?: "",
                thac0 = obj.get("thac0")?.asInt ?: 20,
                armorClass = obj.get("armorClass")?.asInt ?: 10,
                hitDice = obj.get("hitDice")?.asString ?: "",
                hpFull = obj.get("hpFull")?.asInt ?: 0,
                damageRolls = obj.get("damageRolls")?.asString ?: "",
                specialDetections = obj.get("specialDetections")?.asString,
                specialAttacks = obj.get("specialAttacks")?.asString,
                movement = obj.get("movement")?.asString,
                size = obj.get("size")?.asString,
                xp = obj.get("xp")?.asInt ?: 0
            )
        }
    }

    private class PartyDeserializer : JsonDeserializer<Party> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Party {
            val obj = json.asJsonObject
            val id = try { UUID.fromString(obj.get("id")?.asString) } catch (e: Exception) { UUID.randomUUID() }
            val name = obj.get("name")?.asString ?: ""
            val membersElement = obj.get("members")
            val members: MutableList<PartyMember> = if (membersElement != null && membersElement.isJsonArray) {
                context.deserialize(membersElement, object : TypeToken<MutableList<PartyMember>>() {}.type) ?: mutableListOf()
            } else {
                mutableListOf()
            }
            val isActive = obj.get("isActive")?.asBoolean ?: false
            return Party(id, name, members, isActive)
        }
    }

    private class PartyMemberDeserializer : JsonDeserializer<PartyMember> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): PartyMember {
            val obj = json.asJsonObject
            val name = obj.get("name")?.asString ?: ""
            val idElement = obj.get("memberId") ?: obj.get("id")
            val memberId = try {
                if (idElement != null && !idElement.isJsonNull) {
                    UUID.fromString(idElement.asString)
                } else if (name.isNotBlank()) {
                    UUID.nameUUIDFromBytes(name.toByteArray())
                } else UUID.randomUUID()
            } catch (e: Exception) {
                if (name.isNotBlank()) UUID.nameUUIDFromBytes(name.toByteArray())
                else UUID.randomUUID()
            }
            val hpCurrent = obj.get("hpCurrent")?.asInt ?: 0
            val hpFull = obj.get("hpFull")?.asInt ?: 0
            val cloneTagStr = obj.get("cloneTag")?.asString
            val cloneTag = cloneTagStr?.firstOrNull() ?: 0.toChar()
            
            return PartyMember(memberId, hpCurrent, hpFull, cloneTag).apply {
                tempName = name.ifBlank { null }
            }
        }
    }
}
