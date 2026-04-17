package com.google.mediapipe.examples.poselandmarker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.mediapipe.examples.poselandmarker.databinding.ItemPoseReferenceBinding

class PoseReferenceAdapter :
    ListAdapter<PoseReference, PoseReferenceAdapter.PoseReferenceViewHolder>(DiffCallback) {

    var onDeleteReference: ((PoseReference) -> Unit)? = null
    var onSelectReference: ((PoseReference) -> Unit)? = null
    var isPasswordSelectionEnabled: Boolean = false
    var passwordStepLookup: Map<Long, Int> = emptyMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoseReferenceViewHolder {
        val binding = ItemPoseReferenceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PoseReferenceViewHolder(binding, onDeleteReference, onSelectReference)
    }

    override fun onBindViewHolder(holder: PoseReferenceViewHolder, position: Int) {
        holder.bind(
            reference = getItem(position),
            passwordStep = passwordStepLookup[getItem(position).id],
            isPasswordSelectionEnabled = isPasswordSelectionEnabled
        )
    }

    class PoseReferenceViewHolder(
        private val binding: ItemPoseReferenceBinding,
        private val onDeleteReference: ((PoseReference) -> Unit)?,
        private val onSelectReference: ((PoseReference) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            reference: PoseReference,
            passwordStep: Int?,
            isPasswordSelectionEnabled: Boolean
        ) {
            binding.poseReferenceImage.setImageURI(android.net.Uri.parse(reference.uriString))
            binding.poseReferenceName.text = reference.name
            binding.deletePoseReference.setOnClickListener {
                onDeleteReference?.invoke(reference)
            }
            binding.root.setOnClickListener {
                if (isPasswordSelectionEnabled) {
                    onSelectReference?.invoke(reference)
                }
            }
            binding.passwordStepBadge.visibility =
                if (passwordStep != null) android.view.View.VISIBLE else android.view.View.GONE
            binding.passwordStepBadge.text = passwordStep?.toString().orEmpty()
            binding.selectionOverlay.visibility =
                if (isPasswordSelectionEnabled) android.view.View.VISIBLE else android.view.View.GONE
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<PoseReference>() {
        override fun areItemsTheSame(oldItem: PoseReference, newItem: PoseReference): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PoseReference, newItem: PoseReference): Boolean {
            return oldItem == newItem
        }
    }
}
