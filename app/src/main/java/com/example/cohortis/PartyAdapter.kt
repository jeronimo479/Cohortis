package com.example.cohortis

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.cohortis.databinding.ItemPartyBinding

/**
 * Adapter for the top-level RecyclerView that displays a list of [Party] objects.
 * Each party item contains its own nested RecyclerView for its members.
 *
 * @param initialParties The initial list of parties to display.
 * @param onHpChanged Callback invoked when a member's HP is modified.
 * @param onDamageTapped Callback invoked when a damage roll is requested for a member.
 * @param onMemberLongTapped Callback invoked when a member item is long-pressed.
 * @param onOpenPartyLibrary Callback invoked when the party name is clicked to open the library.
 * @param onPartyRenameRequested Callback invoked when a party rename is requested.
 * @param onOpenMemberLibrary Callback invoked when the "Add Member" button is clicked for a party.
 */
class PartyAdapter(
    initialParties: MutableList<Party>,
    private val onHpChanged: (Member, Int) -> Unit,
    private val onDamageTapped: (Member, String) -> Int,
    private val onMemberLongTapped: (Member, Party) -> Unit,
    private val onOpenPartyLibrary: (Party) -> Unit,
    private val onPartyRenameRequested: (Party) -> Unit,
    private val onOpenMemberLibrary: (Party) -> Unit
) : RecyclerView.Adapter<PartyAdapter.PartyViewHolder>() {

    private var displayedParties: MutableList<Party> = initialParties.toMutableList()

    /**
     * ViewHolder for a single Party item.
     */
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
            
            // Nested adapter for members within the party
            val memberAdapter = MemberAdapter(
                party.members,
                onHpChanged,
                onDamageTapped,
                { member -> onMemberLongTapped(member, party) }
            )
            rvMembers.layoutManager = LinearLayoutManager(root.context)
            rvMembers.adapter = memberAdapter

            tvPartyName.setOnClickListener {
                onOpenPartyLibrary(party)
            }

            // Long clicks are currently disabled for the header but can be re-enabled if needed
            tvPartyName.setOnLongClickListener(null)
            partyHeader.setOnLongClickListener(null)

            btnAddMember.setOnClickListener {
                onOpenMemberLibrary(party)
            }
        }
    }

    override fun getItemCount(): Int = displayedParties.size

    /**
     * Updates the list of parties being displayed and refreshes the UI.
     *
     * @param newList The new list of parties.
     */
    fun updateList(newList: List<Party>) {
        displayedParties.clear()
        displayedParties.addAll(newList)
        notifyDataSetChanged()
    }
}
