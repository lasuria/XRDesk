package com.xrdesk

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import com.xrdesk.databinding.DialogColorPickerBinding
import com.google.android.material.chip.Chip

/**
 * Material color picker dialog with Hex, RGB and Palette support.
 */
class ColorPickerDialog(
    context: Context,
    initialColor: Int,
    private val onColorSelected: (Int) -> Unit
) : AlertDialog(context) {

    private lateinit var binding: DialogColorPickerBinding
    private var currentColor: Int = initialColor

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = DialogColorPickerBinding.inflate(layoutInflater)
        setView(binding.root)
        
        setTitle(context.getString(R.string.color_picker_title))
        setButton(BUTTON_POSITIVE, context.getString(R.string.color_picker_select)) { _, _ -> 
            onColorSelected(currentColor) 
        }
        setButton(BUTTON_NEGATIVE, context.getString(R.string.color_picker_cancel)) { _, _ -> 
            dismiss() 
        }

        setupUI()
        super.onCreate(savedInstanceState)
    }

    private fun setupUI() {
        updateFromColor(currentColor, updateSeekbars = true, updateHex = true)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val str = s?.toString() ?: ""
                if (str.length == 7 && str.startsWith("#")) {
                    try {
                        val color = Color.parseColor(str)
                        updateFromColor(color, updateSeekbars = true, updateHex = false)
                    } catch (e: Exception) {}
                }
            }
        }
        binding.hexInput.addTextChangedListener(watcher)

        val seekListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val r = binding.seekRed.progress
                    val g = binding.seekGreen.progress
                    val b = binding.seekBlue.progress
                    updateFromColor(Color.rgb(r, g, b), updateSeekbars = false, updateHex = true)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
        binding.seekRed.setOnSeekBarChangeListener(seekListener)
        binding.seekGreen.setOnSeekBarChangeListener(seekListener)
        binding.seekBlue.setOnSeekBarChangeListener(seekListener)

        val palette = listOf(
            0xFFF44336.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(),
            0xFF673AB7.toInt(), 0xFF3F51B5.toInt(), 0xFF2196F3.toInt(),
            0xFF03A9F4.toInt(), 0xFF00BCD4.toInt(), 0xFF009688.toInt(),
            0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), 0xFFCDDC39.toInt(),
            0xFFFFEB3B.toInt(), 0xFFFFC107.toInt(), 0xFFFF9800.toInt(),
            0xFFFF5722.toInt(), 0xFF795548.toInt(), 0xFF9E9E9E.toInt(),
            0xFF607D8B.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt()
        )

        palette.forEach { color ->
            val chip = Chip(context)
            chip.chipIcon = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, android.R.drawable.checkbox_on_background)
            chip.chipIconTint = android.content.res.ColorStateList.valueOf(color)
            chip.setOnClickListener {
                updateFromColor(color, updateSeekbars = true, updateHex = true)
            }
            binding.paletteGroup.addView(chip)
        }
    }

    private fun updateFromColor(color: Int, updateSeekbars: Boolean, updateHex: Boolean) {
        currentColor = color
        binding.colorPreview.setBackgroundColor(color)
        
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        
        binding.labelRed.text = context.getString(R.string.color_picker_red, r)
        binding.labelGreen.text = context.getString(R.string.color_picker_green, g)
        binding.labelBlue.text = context.getString(R.string.color_picker_blue, b)
        
        if (updateSeekbars) {
            binding.seekRed.progress = r
            binding.seekGreen.progress = g
            binding.seekBlue.progress = b
        }
        
        if (updateHex) {
            val hex = String.format("#%06X", 0xFFFFFF and color)
            binding.hexInput.setText(hex)
        }
    }
}
