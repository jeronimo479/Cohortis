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
        onOpenMemberLibrary: (Party) -> Unit
    ) {
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
    }

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
