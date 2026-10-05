package com.applockerapp.presentation.intruder

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.applockerapp.R
import com.applockerapp.databinding.FragmentIntruderSelfieBinding
import com.applockerapp.utils.LockSettingsManager
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class IntruderSelfieFragment : Fragment() {

    private var _binding: FragmentIntruderSelfieBinding? = null
    private val binding get() = _binding!!

    private lateinit var lockSettingsManager: LockSettingsManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntruderSelfieBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lockSettingsManager = LockSettingsManager(requireContext())
        setupViews()
        setupListeners()
        loadPhotos()
    }

    private fun setupViews() {
        val isEnabled = lockSettingsManager.isIntruderEnabled()
        binding.switchCapture.isChecked = isEnabled
        updateGalleryVisibility(isEnabled)

        // Set limits chip state based on saved settings
        when (lockSettingsManager.getIntruderLimit()) {
            1 -> binding.chipGroupAttempts.check(R.id.chipAttempt1)
            2 -> binding.chipGroupAttempts.check(R.id.chipAttempt2)
            3 -> binding.chipGroupAttempts.check(R.id.chipAttempt3)
        }
    }

    private fun setupListeners() {
        binding.btnBackIntruder.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.switchCapture.setOnCheckedChangeListener { _, isChecked ->
            lockSettingsManager.setIntruderEnabled(isChecked)
            updateGalleryVisibility(isChecked)
            val status = if (isChecked) "enabled" else "disabled"
            Toast.makeText(context, "Intruder Selfie $status!", Toast.LENGTH_SHORT).show()
        }

        binding.chipAttempt1.setOnClickListener {
            lockSettingsManager.setIntruderLimit(1)
            Toast.makeText(context, "Snap photo on 1st failed attempt.", Toast.LENGTH_SHORT).show()
        }
        binding.chipAttempt2.setOnClickListener {
            lockSettingsManager.setIntruderLimit(2)
            Toast.makeText(context, "Snap photo on 2nd failed attempt.", Toast.LENGTH_SHORT).show()
        }
        binding.chipAttempt3.setOnClickListener {
            lockSettingsManager.setIntruderLimit(3)
            Toast.makeText(context, "Snap photo on 3rd failed attempt.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateGalleryVisibility(isEnabled: Boolean) {
        if (isEnabled) {
            binding.gridIntruders.visibility = View.VISIBLE
            binding.cardEmptyGallery.visibility = View.GONE
            binding.cardAttemptLimit.alpha = 1.0f
            binding.chipAttempt1.isEnabled = true
            binding.chipAttempt2.isEnabled = true
            binding.chipAttempt3.isEnabled = true
        } else {
            binding.gridIntruders.visibility = View.GONE
            binding.cardEmptyGallery.visibility = View.VISIBLE
            binding.cardAttemptLimit.alpha = 0.5f
            binding.chipAttempt1.isEnabled = false
            binding.chipAttempt2.isEnabled = false
            binding.chipAttempt3.isEnabled = false
        }
    }

    private fun loadPhotos() {
        val dir = File(requireContext().filesDir, "intruder_selfies")
        val files = if (dir.exists()) {
            dir.listFiles()?.filter { it.name.startsWith("selfie_") && it.name.endsWith(".jpg") }
                ?.sortedByDescending { it.lastModified() } ?: emptyList()
        } else {
            emptyList()
        }

        binding.gridIntruders.removeAllViews()

        if (files.isEmpty() && lockSettingsManager.isIntruderEnabled()) {
            binding.gridIntruders.visibility = View.GONE
            binding.cardEmptyGallery.visibility = View.VISIBLE
        } else if (lockSettingsManager.isIntruderEnabled()) {
            binding.gridIntruders.visibility = View.VISIBLE
            binding.cardEmptyGallery.visibility = View.GONE

            val context = requireContext()
            val density = resources.displayMetrics.density
            
            for (file in files) {
                // Card container
                val card = com.google.android.material.card.MaterialCardView(context).apply {
                    radius = 16f * density
                    cardElevation = 0f
                    setCardBackgroundColor(ContextCompat.getColorStateList(context, R.color.bg_surface_card))
                    strokeWidth = (1f * density).toInt()
                    setStrokeColor(ContextCompat.getColorStateList(context, R.color.translucent_white))
                    
                    val layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = (140 * density).toInt()
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(
                            (6 * density).toInt(),
                            (6 * density).toInt(),
                            (6 * density).toInt(),
                            (6 * density).toInt()
                        )
                    }
                    this.layoutParams = layoutParams
                }

                val frame = android.widget.FrameLayout(context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                val image = ImageView(context).apply {
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageURI(Uri.fromFile(file))
                }

                // Parse filename details: selfie_{timestamp}_{package}_{attempts}.jpg
                val nameParts = file.nameWithoutExtension.split("_")
                var dateStr = "Unknown Date"
                var detailStr = ""
                if (nameParts.size >= 4) {
                    val timestamp = nameParts[1].toLongOrNull()
                    val pkgName = nameParts[2]
                    val attempts = nameParts[3]
                    
                    if (timestamp != null) {
                        val date = Date(timestamp)
                        val format = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
                        dateStr = format.format(date)
                    }
                    
                    var appLabel = pkgName
                    try {
                        val pm = context.packageManager
                        val info = pm.getApplicationInfo(pkgName, 0)
                        appLabel = pm.getApplicationLabel(info).toString()
                    } catch (e: Exception) {}
                    
                    detailStr = "Target: $appLabel\nWrong Attempts: $attempts"
                }

                val text = TextView(context).apply {
                    val textParams = android.widget.FrameLayout.LayoutParams(
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                        bottomMargin = (8 * density).toInt()
                    }
                    layoutParams = textParams
                    setBackgroundColor(0xA0000000.toInt())
                    setPadding(
                        (8 * density).toInt(),
                        (2 * density).toInt(),
                        (8 * density).toInt(),
                        (2 * density).toInt()
                    )
                    setText(dateStr)
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                    textSize = 10f
                }

                frame.addView(image)
                frame.addView(text)
                card.addView(frame)

                card.setOnClickListener {
                    showIntruderZoomDialog(file, dateStr, detailStr)
                }

                binding.gridIntruders.addView(card)
            }
        }
    }

    private fun showIntruderZoomDialog(file: File, timeStr: String, reasonStr: String) {
        val context = requireContext()
        val builder = AlertDialog.Builder(context)
        
        // Custom inflated zoom layout inside alertdialog
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(24, 24, 24, 24)
        }

        val imageView = ImageView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (320 * resources.displayMetrics.density).toInt()
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageURI(Uri.fromFile(file))
        }

        val detailView = TextView(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16
            }
            text = "Captured: $timeStr\n$reasonStr"
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 14f
        }

        container.addView(imageView)
        container.addView(detailView)

        builder.setView(container)
        builder.setTitle("Intruder Selfie Alert!")
        
        builder.setPositiveButton("Close") { dialog, _ ->
            dialog.dismiss()
        }
        
        builder.setNegativeButton("Delete Snapshot") { dialog, _ ->
            if (file.exists() && file.delete()) {
                Toast.makeText(context, "Snapshot deleted successfully.", Toast.LENGTH_SHORT).show()
                loadPhotos()
            } else {
                Toast.makeText(context, "Failed to delete snapshot.", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.create().show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
