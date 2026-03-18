package com.example.cohortis

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class DataManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("CohortisPrefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // Caching layer for performance
    private var _memberLibrary: MutableList<Member>? = null
    private var _partyLibrary: MutableList<Party>? = null
    private var _activeParties: MutableList<Party>? = null

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

    var currentRound: Int
        get() = prefs.getInt("currentRound", 0)
        set(value) {
            prefs.edit().putInt("currentRound", value).apply()
        }

    fun saveAll(members: List<Member>, parties: List<Party>, active: List<Party>, round: Int) {
        memberLibrary = members.toMutableList()
        partyLibrary = parties.toMutableList()
        activeParties = active.toMutableList()
        currentRound = round
    }

    fun clearAll() {
        _memberLibrary = null
        _partyLibrary = null
        _activeParties = null
        prefs.edit().clear().apply()
    }
}
