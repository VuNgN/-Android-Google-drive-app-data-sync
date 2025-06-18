package com.example.googledrive

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil.ItemCallback
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class FileAdapter(
    private val onDeleteClick: (MainActivity.LocalFileInfo) -> Unit
) : ListAdapter<MainActivity.LocalFileInfo, FileAdapter.FileViewHolder>(FileDiffCallback()) {

    inner class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val fileNameTextView: TextView = view.findViewById(R.id.fileNameTextView)
        private val deleteButton: Button = view.findViewById(R.id.deleteButton)

        fun bind(file: MainActivity.LocalFileInfo) {
            fileNameTextView.text = file.name
            deleteButton.setOnClickListener {
                onDeleteClick(file)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.file_item, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = currentList[position]
        holder.bind(file)
    }

    class FileDiffCallback : ItemCallback<MainActivity.LocalFileInfo>() {
        override fun areItemsTheSame(
            oldItem: MainActivity.LocalFileInfo,
            newItem: MainActivity.LocalFileInfo
        ): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(
            oldItem: MainActivity.LocalFileInfo,
            newItem: MainActivity.LocalFileInfo
        ): Boolean {
            return oldItem == newItem
        }
    }
} 