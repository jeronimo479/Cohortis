package com.example.cohortis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.cohortis.databinding.FragmentPartyBinding

class PartyFragment : Fragment() {
    private var _binding: FragmentPartyBinding? = null
    private val binding get() = _binding!!
    private lateinit var partyAdapter: PartyAdapter
    private var onOpenPartyLibrary: ((Party?) -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPartyBinding.inflate(inflater, container, false)
        return binding.root
    }

    fun setupRecyclerView(
        parties: MutableList<Party>,
        onHpChanged: (Member, Int) -> Unit,
        onDamageTapped: (Member, String) -> Int,
        onMemberLongTapped: (Member, Party) -> Unit,
        onOpenPartyLibrary: (Party?) -> Unit,
        onPartyRenameRequested: (Party) -> Unit,
        onOpenMemberLibrary: (Party) -> Unit,
        onNewPartyRequested: () -> Unit
    ) {
        this.onOpenPartyLibrary = onOpenPartyLibrary
        
        partyAdapter = PartyAdapter(
            parties, 
            onHpChanged, 
            onDamageTapped, 
            onMemberLongTapped,
            { party -> onOpenPartyLibrary(party) },
            onPartyRenameRequested,
            onOpenMemberLibrary
        )
        binding.rvParties.layoutManager = LinearLayoutManager(context)
        binding.rvParties.adapter = partyAdapter
        
        binding.tvEmptyManageLibrary.setOnClickListener {
            onOpenPartyLibrary(null)
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
        // Active parties are those not marked INACTIVE
        val visibleParties = list.filter { it.status != PartyStatus.INACTIVE }
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
