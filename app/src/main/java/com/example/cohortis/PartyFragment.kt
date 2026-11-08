package com.example.cohortis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cohortis.databinding.FragmentPartyBinding
import java.util.UUID

/**
 * Fragment responsible for displaying the list of active parties and their members.
 * Acts as a container for the [PartyAdapter].
 */
class PartyFragment : Fragment() {
    private var _binding: FragmentPartyBinding? = null
    private val binding get() = _binding!!
    private lateinit var partyAdapter: PartyAdapter

    private var pendingSetup: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        pendingSetup?.invoke()
        pendingSetup = null
    }

    /**
     * Initializes the [RecyclerView] with the [PartyAdapter] and provided callbacks.
     */
    fun setupRecyclerView(
        parties: MutableList<Party>,
        getTemplate: (UUID) -> Member?,
        onHpChanged: (PartyMember, Member, Int) -> Unit,
        onHpComplete: (PartyMember, Member, Int, Int) -> Unit,
        onDamageTapped: (Member, String) -> Int,
        onMemberLongTapped: (PartyMember, Member, Party) -> Unit,
        onOpenPartyLibrary: (Party?) -> Unit,
        onOpenMemberLibrary: (Party) -> Unit,
        onCreateMemberRequested: (Party) -> Unit
    ) {
        val action = {
            partyAdapter = PartyAdapter(
                parties, 
                getTemplate,
                onHpChanged,
                onHpComplete,
                onDamageTapped, 
                onMemberLongTapped,
                { party -> onOpenPartyLibrary(party) },
                onOpenMemberLibrary,
                onCreateMemberRequested
            )
            binding.rvParties.layoutManager = LinearLayoutManager(context)
            binding.rvParties.adapter = partyAdapter
        }

        if (_binding != null) {
            action()
        } else {
            pendingSetup = action
        }
    }

    /**
     * Updates the adapter with a fresh list of parties.
     */
    fun updateParties(newList: List<Party>) {
        if (::partyAdapter.isInitialized) {
            partyAdapter.updateList(newList)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
