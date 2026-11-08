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
    val priorityPartyId: UUID? = null,
    val members: List<Member> = emptyList(),
    val parties: List<Party> = emptyList()
)

/**
 * Manages the persistence of application data using [SharedPreferences] and [Gson].
 * Uses custom deserializers to handle schema changes and maintain data integrity.
 */
class DataManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("CohortisPrefs", Context.MODE_PRIVATE)
    
    /**
     * Custom GSON instance configured with robust serializers and deserializers.
     */
    val gson: Gson = GsonBuilder()
        .setLenient()
        .registerTypeAdapter(LibraryExport::class.java, LibraryExportSerializer())
        .registerTypeAdapter(Member::class.java, MemberSerializer())
        .registerTypeAdapter(Member::class.java, MemberDeserializer())
        .registerTypeAdapter(Party::class.java, PartySerializer())
        .registerTypeAdapter(Party::class.java, PartyDeserializer())
        .registerTypeAdapter(PartyMember::class.java, PartyMemberSerializer())
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
            saveAll(memberLibrary, partyLibrary, priorityPartyId, currentRound)
        }
    }

    /** Global member library. Loads from disk on first access. */
    var memberLibrary: MutableList<Member>
        get() {
            if (_memberLibrary == null) {
                val json = prefs.getString(KEY_MEMBER_LIBRARY, null)
                _memberLibrary = try {
                    if (json.isNullOrBlank()) mutableListOf()
                    else {
                        val list: MutableList<Member> = gson.fromJson(json, object : TypeToken<MutableList<Member>>() {}.type) ?: mutableListOf()
                        list.filter { it.name.isNotBlank() }.toMutableList()
                    }
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

    /** Parties currently marked as active. Computed from partyLibrary. */
    var activeParties: MutableList<Party>
        get() {
            if (_activeParties == null) {
                _activeParties = partyLibrary.filter { it.isActive }.toMutableList()
            }
            return _activeParties!!
        }
        set(value) {
            _activeParties = value
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
    fun saveAll(members: List<Member>, parties: List<Party>, priorityId: UUID?, round: Int) {
        _memberLibrary = members.filter { it.name.isNotBlank() }.toMutableList()
        _partyLibrary = parties.toMutableList()
        _activeParties = parties.filter { it.isActive }.toMutableList()
        
        prefs.edit().apply {
            putString(KEY_MEMBER_LIBRARY, gson.toJson(_memberLibrary))
            putString(KEY_PARTY_LIBRARY, gson.toJson(_partyLibrary))
            remove(KEY_ACTIVE_PARTIES)
            putString(KEY_PRIORITY_ID, priorityId?.toString())
            putInt(KEY_CURRENT_ROUND, round)
            putInt(KEY_SCHEMA_VERSION, CURRENT_SCHEMA_VERSION)
        }.apply()
    }

    fun clearAll() {
        _memberLibrary = mutableListOf()
        _partyLibrary = mutableListOf()
        _activeParties = mutableListOf()
        prefs.edit().clear().apply()
        saveAll(_memberLibrary!!, _partyLibrary!!, null, 0)
    }

    private class LibraryExportSerializer : JsonSerializer<LibraryExport> {
        override fun serialize(src: LibraryExport, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()
            obj.addProperty("version", src.version)
            if (src.priorityPartyId != null) {
                obj.addProperty("priorityPartyId", src.priorityPartyId.toString())
            }
            obj.add("members", context.serialize(src.members))
            obj.add("parties", context.serialize(src.parties))
            return obj
        }
    }

    private class MemberSerializer : JsonSerializer<Member> {
        override fun serialize(src: Member, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()
            obj.addProperty("alias", src.name)
            obj.addProperty("id", src.id.toString())
            if (src.isPC) obj.addProperty("isPC", true)
            if (src.classLevels.isNotBlank()) obj.addProperty("classLevels", src.classLevels)
            if (src.thac0 != 20) obj.addProperty("thac0", src.thac0)
            if (src.armorClass != 10) obj.addProperty("armorClass", src.armorClass)
            if (src.hitDice.isNotBlank()) obj.addProperty("hitDice", src.hitDice)
            if (src.hpFull != 0) obj.addProperty("hpFull", src.hpFull)
            if (src.damageRolls.isNotBlank()) obj.addProperty("damageRolls", src.damageRolls)
            if (src.specialDetections.isNotBlank()) obj.addProperty("specialDetections", src.specialDetections)
            if (src.specialAttacks.isNotBlank()) obj.addProperty("specialAttacks", src.specialAttacks)
            if (src.movement.isNotBlank()) obj.addProperty("movement", src.movement)
            if (src.size.isNotBlank()) obj.addProperty("size", src.size)
            if (src.xp != 0) obj.addProperty("xp", src.xp)
            return obj
        }
    }

    private class PartyMemberSerializer : JsonSerializer<PartyMember> {
        override fun serialize(src: PartyMember, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()
            if (!src.name.isNullOrBlank()) obj.addProperty("alias", src.name)
            obj.addProperty("memberId", src.memberId.toString())
            if (src.hpCurrent != 0) obj.addProperty("hpCurrent", src.hpCurrent)
            if (src.hpFull != 0) obj.addProperty("hpFull", src.hpFull)
            if (src.cloneTag != 0.toChar() && src.cloneTag != '\u0000') {
                obj.addProperty("cloneTag", src.cloneTag.toString())
            }
            return obj
        }
    }

    private class PartySerializer : JsonSerializer<Party> {
        override fun serialize(src: Party, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val obj = JsonObject()
            obj.addProperty("alias", src.name)
            obj.addProperty("id", src.id.toString())
            if (src.isActive) obj.addProperty("isActive", true)
            obj.add("members", context.serialize(src.members))
            return obj
        }
    }

    private class MemberDeserializer : JsonDeserializer<Member> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Member {
            if (!json.isJsonObject) return Member()
            val obj = json.asJsonObject
            val nameEl = obj.get("alias") ?: obj.get("_name_") ?: obj.get("name")
            val name = if (nameEl != null && nameEl.isJsonPrimitive) nameEl.asString else ""
            
            val idEl = obj.get("id")
            val idStr = if (idEl != null && idEl.isJsonPrimitive) idEl.asString else null
            
            val id = try {
                if (!idStr.isNullOrBlank()) UUID.fromString(idStr)
                else if (name.isNotBlank()) UUID.nameUUIDFromBytes(name.toByteArray())
                else UUID.randomUUID()
            } catch (e: Exception) {
                if (name.isNotBlank()) UUID.nameUUIDFromBytes(name.toByteArray())
                else UUID.randomUUID()
            }

            fun getStr(key: String): String {
                val el = obj.get(key)
                return if (el != null && el.isJsonPrimitive) el.asString else ""
            }

            fun getInt(key: String, defaultVal: Int): Int {
                val el = obj.get(key)
                return try {
                    if (el != null && el.isJsonPrimitive) el.asInt else defaultVal
                } catch (_: Exception) { defaultVal }
            }

            fun getBool(key: String, defaultVal: Boolean): Boolean {
                val el = obj.get(key)
                return try {
                    if (el != null && el.isJsonPrimitive) el.asBoolean else defaultVal
                } catch (_: Exception) { defaultVal }
            }

            return Member(
                name = name,
                id = id,
                isPC = getBool("isPC", false),
                classLevels = getStr("classLevels"),
                thac0 = getInt("thac0", 20),
                armorClass = getInt("armorClass", 10),
                hitDice = getStr("hitDice"),
                hpFull = getInt("hpFull", 0),
                damageRolls = getStr("damageRolls"),
                specialDetections = getStr("specialDetections"),
                specialAttacks = getStr("specialAttacks"),
                movement = getStr("movement"),
                size = getStr("size"),
                xp = getInt("xp", 0)
            )
        }
    }

    private class PartyDeserializer : JsonDeserializer<Party> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Party {
            if (!json.isJsonObject) return Party()
            val obj = json.asJsonObject
            
            val idEl = obj.get("id")
            val idStr = if (idEl != null && idEl.isJsonPrimitive) idEl.asString else null
            val id = try { if (!idStr.isNullOrBlank()) UUID.fromString(idStr) else UUID.randomUUID() } catch (e: Exception) { UUID.randomUUID() }
            
            val nameEl = obj.get("alias") ?: obj.get("_name_") ?: obj.get("name")
            val name = if (nameEl != null && nameEl.isJsonPrimitive) nameEl.asString else ""
            
            val membersElement = obj.get("members")
            val members: MutableList<PartyMember> = if (membersElement != null && membersElement.isJsonArray) {
                context.deserialize(membersElement, object : TypeToken<MutableList<PartyMember>>() {}.type) ?: mutableListOf()
            } else {
                mutableListOf()
            }
            
            val activeEl = obj.get("isActive")
            val isActive = try { if (activeEl != null && activeEl.isJsonPrimitive) activeEl.asBoolean else false } catch (_: Exception) { false }
            
            return Party(name, id, members, isActive)
        }
    }

    private class PartyMemberDeserializer : JsonDeserializer<PartyMember> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): PartyMember {
            if (!json.isJsonObject) return PartyMember(memberId = UUID.randomUUID())
            val obj = json.asJsonObject
            
            val nameEl = obj.get("alias") ?: obj.get("_name_") ?: obj.get("name")
            val name = if (nameEl != null && nameEl.isJsonPrimitive) nameEl.asString.ifBlank { null } else null
            
            val idElement = obj.get("memberId") ?: obj.get("id")
            val idStr = if (idElement != null && idElement.isJsonPrimitive) idElement.asString else null
            val memberId = try {
                if (!idStr.isNullOrBlank()) {
                    UUID.fromString(idStr)
                } else if (!name.isNullOrBlank()) {
                    UUID.nameUUIDFromBytes(name.toByteArray())
                } else UUID.randomUUID()
            } catch (e: Exception) {
                if (!name.isNullOrBlank()) UUID.nameUUIDFromBytes(name.toByteArray())
                else UUID.randomUUID()
            }
            
            fun getInt(key: String, defaultVal: Int): Int {
                val el = obj.get(key)
                return try {
                    if (el != null && el.isJsonPrimitive) el.asInt else defaultVal
                } catch (_: Exception) { defaultVal }
            }

            val hpCurrent = getInt("hpCurrent", 0)
            val hpFull = getInt("hpFull", 0)
            
            val cloneTagEl = obj.get("cloneTag")
            val cloneTagStr = if (cloneTagEl != null && cloneTagEl.isJsonPrimitive) cloneTagEl.asString else null
            val cloneTag = cloneTagStr?.firstOrNull() ?: 0.toChar()
            
            return PartyMember(name, memberId, hpCurrent, hpFull, cloneTag).apply {
                tempName = name
            }
        }
    }
}
