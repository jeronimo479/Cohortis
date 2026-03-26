package com.example.cohortis

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manages the persistence of application data using [SharedPreferences] and [Gson].
 * Provides a caching layer to minimize redundant disk I/O.
 *
 * @param context The context used to access SharedPreferences.
 */
class DataManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("CohortisPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Caching layer for performance
    private var _memberLibrary: MutableList<Member>? = null
    private var _partyLibrary: MutableList<Party>? = null
    private var _activeParties: MutableList<Party>? = null

    /**
     * Accesses the list of all members saved in the library.
     * Loads from SharedPreferences on first access and caches the result.
     * Saving to this property updates both the cache and SharedPreferences.
     */
    var memberLibrary: MutableList<Member>
        get() {
            if (_memberLibrary == null) {
                val json = prefs.getString("memberLibrary", null)
                _memberLibrary = if (json == null) mutableListOf()
                else gson.fromJson(json, object : TypeToken<MutableList<Member>>() {}.type)
            }
            return _memberLibrary!!
        }
        set(value) {
            _memberLibrary = value
            prefs.edit().putString("memberLibrary", gson.toJson(value)).apply()
        }

    /**
     * Accesses the list of all parties saved in the library.
     * Loads from SharedPreferences on first access and caches the result.
     * Saving to this property updates both the cache and SharedPreferences.
     */
    var partyLibrary: MutableList<Party>
        get() {
            if (_partyLibrary == null) {
                val json = prefs.getString("partyLibrary", null)
                _partyLibrary = if (json == null) mutableListOf()
                else gson.fromJson(json, object : TypeToken<MutableList<Party>>() {}.type)
            }
            return _partyLibrary!!
        }
        set(value) {
            _partyLibrary = value
            prefs.edit().putString("partyLibrary", gson.toJson(value)).apply()
        }

    /**
     * Accesses the list of parties currently marked as active.
     * Loads from SharedPreferences on first access and caches the result.
     * Saving to this property updates both the cache and SharedPreferences.
     */
    var activeParties: MutableList<Party>
        get() {
            if (_activeParties == null) {
                val json = prefs.getString("activeParties", null)
                _activeParties = if (json == null) mutableListOf()
                else gson.fromJson(json, object : TypeToken<MutableList<Party>>() {}.type)
            }
            return _activeParties!!
        }
        set(value) {
            _activeParties = value
            prefs.edit().putString("activeParties", gson.toJson(value)).apply()
        }

    /**
     * Accesses the current round number from the round counter.
     */
    var currentRound: Int
        get() = prefs.getInt("currentRound", 0)
        set(value) {
            prefs.edit().putInt("currentRound", value).apply()
        }

    /**
     * Saves all relevant application data to persistence in one call.
     *
     * @param members The list of all members.
     * @param parties The list of all parties.
     * @param active The list of active parties.
     * @param round The current round number.
     */
    fun saveAll(members: List<Member>, parties: List<Party>, active: List<Party>, round: Int) {
        memberLibrary = members.toMutableList()
        partyLibrary = parties.toMutableList()
        activeParties = active.toMutableList()
        currentRound = round
    }

    /**
     * Clears all saved data from SharedPreferences and resets the cache.
     */
    fun clearAll() {
        _memberLibrary = null
        _partyLibrary = null
        _activeParties = null
        prefs.edit().clear().apply()
    }
}
