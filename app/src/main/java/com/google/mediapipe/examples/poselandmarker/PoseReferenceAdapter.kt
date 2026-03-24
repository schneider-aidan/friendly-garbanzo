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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PoseReferenceViewHolder {
        val binding = ItemPoseReferenceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PoseReferenceViewHolder(binding, onDeleteReference)
    }

    override fun onBindViewHolder(holder: PoseReferenceViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class PoseReferenceViewHolder(
        private val binding: ItemPoseReferenceBinding,
        private val onDeleteReference: ((PoseReference) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(reference: PoseReference) {
            binding.poseReferenceImage.setImageURI(android.net.Uri.parse(reference.uriString))
            binding.poseReferenceName.text = reference.name
            binding.deletePoseReference.setOnClickListener {
                onDeleteReference?.invoke(reference)
            }
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
