package com.sharks.androidinktablet.ui

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.sharks.androidinktablet.R

/**
 * Color picker dialog with predefined color palette
 */
class ColorPickerDialog : DialogFragment() {
    
    private var selectedColor: Int = Color.BLACK
    private var onColorSelectedListener: ((Int) -> Unit)? = null
    
    private val colorPalette = arrayOf(
        Color.BLACK,
        Color.parseColor("#1976D2"), // Blue
        Color.parseColor("#F44336"), // Red
        Color.parseColor("#4CAF50"), // Green
        Color.parseColor("#9C27B0"), // Purple
        Color.parseColor("#FF9800"), // Orange
        Color.parseColor("#795548"), // Brown
        Color.parseColor("#607D8B"), // Blue Gray
        Color.parseColor("#E91E63"), // Pink
        Color.parseColor("#2196F3"), // Light Blue
        Color.parseColor("#8BC34A"), // Light Green
        Color.parseColor("#FFEB3B"), // Yellow
        Color.parseColor("#FF5722"), // Deep Orange
        Color.parseColor("#9E9E9E"), // Gray
        Color.parseColor("#3F51B5"), // Indigo
        Color.parseColor("#00BCD4"), // Cyan
        Color.DKGRAY,
        Color.WHITE
    )
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_color_picker, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val recyclerView = view.findViewById<RecyclerView>(R.id.colorRecyclerView)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSelect = view.findViewById<MaterialButton>(R.id.btnSelect)
        
        recyclerView.layoutManager = GridLayoutManager(context, 6)
        recyclerView.adapter = ColorAdapter(colorPalette) { color ->
            selectedColor = color
        }
        
        btnCancel.setOnClickListener { dismiss() }
        btnSelect.setOnClickListener { 
            onColorSelectedListener?.invoke(selectedColor)
            dismiss()
        }
    }
    
    fun setOnColorSelectedListener(listener: (Int) -> Unit) {
        onColorSelectedListener = listener
    }
    
    fun setInitialColor(color: Int) {
        selectedColor = color
    }
    
    private inner class ColorAdapter(
        private val colors: Array<Int>,
        private val onColorClick: (Int) -> Unit
    ) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {
        
        private var selectedPosition = 0
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_color, parent, false)
            return ColorViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
            holder.bind(colors[position], position == selectedPosition)
        }
        
        override fun getItemCount(): Int = colors.size
        
        inner class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val colorView: ImageView = itemView.findViewById(R.id.colorView)
            private val selectionIndicator: View = itemView.findViewById(R.id.selectionIndicator)
            
            fun bind(color: Int, isSelected: Boolean) {
                colorView.setColorFilter(color)
                selectionIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
                
                itemView.setOnClickListener {
                    val previousPosition = selectedPosition
                    selectedPosition = bindingAdapterPosition
                    notifyItemChanged(previousPosition)
                    notifyItemChanged(selectedPosition)
                    onColorClick(color)
                }
            }
        }
    }
}