package com.example.cohortis2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cohortis2.databinding.FragmentPartyBinding

class PartyFragment : Fragment() {
    private var _binding: FragmentPartyBinding? = null
    private val binding get() = _binding!!
    private lateinit var partyAdapter: PartyAdapter
    private var onNewPartyRequested: (() -> Unit)? = null
    private var onOpenMemberLibrary: ((Party) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.emptyPartyHeader.setOnClickListener {
            onNewPartyRequested?.invoke()
        }
        binding.emptyAddMember.setOnClickListener {
            // Tapping (+) on the empty view doesn't have a specific party,
            // but we can pass a dummy one or handle it in MainActivity.
            // For now, let's just trigger the callback if it's set.
            // Since we don't want to add members to the default empty party, 
            // we could open the library without a target party.
        }
    }

    fun setupRecyclerView(
        parties: MutableList<Party>,
        onHpChanged: (Member) -> Unit,
        onDamageTapped: (Member, String) -> Unit,
        onCharacterLongTapped: (Member, Party) -> Unit,
        onOpenPartyLibrary: (Party) -> Unit,
        onPartyRenameRequested: (Party) -> Unit,
        onOpenMemberLibrary: (Party) -> Unit,
        onNewPartyRequested: () -> Unit
    ) {
        this.onNewPartyRequested = onNewPartyRequested
        this.onOpenMemberLibrary = onOpenMemberLibrary
        
        partyAdapter = PartyAdapter(
            parties, 
            onHpChanged, 
            onDamageTapped, 
            onCharacterLongTapped,
            onOpenPartyLibrary,
            onPartyRenameRequested,
            onOpenMemberLibrary
        )
        binding.rvParties.layoutManager = LinearLayoutManager(context)
        binding.rvParties.adapter = partyAdapter
        
        // Link the (+) button on the empty state to the library
        binding.emptyAddMember.setOnClickListener {
            // Requested: Tapping/swiping should NOT add to default empty party.
            // We'll just open the library in "browse" mode (no target party).
            // Actually, the user might want to create a party from here.
            onNewPartyRequested()
        }

        updateEmptyState(parties)
    }

    fun updateParties(newList: List<Party>) {
        if (::partyAdapter.isInitialized) {
            partyAdapter.updateList(newList)
            updateEmptyState(newList)
        }
    }

    private fun updateEmptyState(list: List<Party>) {
        val visibleParties = list.filter { it.isVisible }
        if (visibleParties.isEmpty()) {
            binding.emptyPartyBox.visibility = View.VISIBLE
            binding.rvParties.visibility = View.GONE
        } else {
            binding.emptyPartyBox.visibility = View.GONE
            binding.rvParties.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
