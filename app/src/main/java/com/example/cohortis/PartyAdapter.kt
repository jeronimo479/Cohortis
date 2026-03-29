package com.example.cohortis

import android.graphics.Color
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cohortis.databinding.ItemPartyBinding

/**
 * Adapter for the top-level RecyclerView that displays a list of [Party] objects.
 * Each party item contains its own nested RecyclerView for its members.
 */
class PartyAdapter(
    initialParties: MutableList<Party>,
    private val onHpChanged: (Member, Int) -> Unit,
    private val onHpComplete: (Member, Int, Int) -> Unit,
    private val onDamageTapped: (Member, String) -> Int,
    private val onMemberLongTapped: (Member, Party) -> Unit,
    private val onOpenPartyEdit: (Party) -> Unit,
    private val onPartyRenameRequested: (Party) -> Unit,
    private val onOpenMemberLibrary: (Party) -> Unit,
    private val onCreateMemberRequested: (Party) -> Unit
) : RecyclerView.Adapter<PartyAdapter.PartyViewHolder>() {

    private var displayedParties: MutableList<Party> = initialParties.toMutableList()

    class PartyViewHolder(val binding: ItemPartyBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartyViewHolder {
        val binding = ItemPartyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PartyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PartyViewHolder, position: Int) {
        val party = displayedParties[position]
        holder.binding.apply {
            tvPartyName.text = party.name
            tvPartyName.setTextColor(Color.BLACK)
            
            val memberAdapter = MemberAdapter(
                party.members,
                onHpChanged,
                onHpComplete,
                onDamageTapped,
                { member -> onMemberLongTapped(member, party) }
            )
            rvMembers.layoutManager = LinearLayoutManager(root.context)
            rvMembers.adapter = memberAdapter

            tvPartyName.setOnClickListener {
                onOpenPartyEdit(party)
            }

            btnAddMember.setOnClickListener {
                onOpenMemberLibrary(party)
            }

            btnAddMember.setOnLongClickListener { view ->
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onCreateMemberRequested(party)
                true
            }
        }
    }

    override fun getItemCount(): Int = displayedParties.size

    fun updateList(newList: List<Party>) {
        displayedParties.clear()
        displayedParties.addAll(newList)
        notifyDataSetChanged()
    }
}
