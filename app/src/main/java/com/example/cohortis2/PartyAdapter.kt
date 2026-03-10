package com.example.cohortis2

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cohortis2.databinding.ItemPartyBinding

class PartyAdapter(
    initialParties: MutableList<Party>,
    private val onHpChanged: (Member) -> Unit,
    private val onDamageTapped: (Member, String) -> Unit,
    private val onCharacterLongTapped: (Member, Party) -> Unit,
    private val onOpenPartyLibrary: (Party) -> Unit,
    private val onPartyRenameRequested: (Party) -> Unit,
    private val onOpenMemberLibrary: (Party) -> Unit
) : RecyclerView.Adapter<PartyAdapter.PartyViewHolder>() {

    private var displayedParties: MutableList<Party> = initialParties.filter { it.isVisible }.toMutableList()

    class PartyViewHolder(val binding: ItemPartyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartyViewHolder {
        val binding = ItemPartyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PartyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PartyViewHolder, position: Int) {
        val party = displayedParties[position]
        holder.binding.apply {
            tvPartyName.text = party.name
            
            val charAdapter = MemberAdapter(
                party.members,
                onHpChanged,
                onDamageTapped,
                { member -> onCharacterLongTapped(member, party) }
            )
            rvCharacters.layoutManager = LinearLayoutManager(root.context)
            rvCharacters.adapter = charAdapter

            // Tapping party name opens the Party Manager dialog
            tvPartyName.setOnClickListener {
                onOpenPartyLibrary(party)
            }

            // Long tapping does nothing
            tvPartyName.setOnLongClickListener(null)
            partyHeader.setOnLongClickListener(null)

            // (+) button opens Member Library
            btnAddMember.setOnClickListener {
                onOpenMemberLibrary(party)
            }
        }
    }

    override fun getItemCount(): Int = displayedParties.size

    fun updateList(newList: List<Party>) {
        displayedParties.clear()
        displayedParties.addAll(newList.filter { it.isVisible })
        notifyDataSetChanged()
    }
}
