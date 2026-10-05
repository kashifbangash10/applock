package com.applockerapp.presentation.themes

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.applockerapp.R
import com.applockerapp.databinding.FragmentApplyThemeBinding
import com.applockerapp.presentation.customview.PatternLockView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ApplyThemeFragment : Fragment() {

    private var _binding: FragmentApplyThemeBinding? = null
    private val binding get() = _binding!!

    private var themeId: Int = 1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApplyThemeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        themeId = arguments?.getInt("themeId") ?: 1
        setupThemePreview()
        setupListeners()
    }

    private fun setupThemePreview() {
        val theme = ThemeConfig.getById(themeId)

        // Background
        binding.imgApplyThemeBg.setImageResource(theme.drawableRes)
        binding.imgApplyThemeBg.scaleType = if (theme.isImage) {
            android.widget.ImageView.ScaleType.CENTER_CROP
        } else {
            android.widget.ImageView.ScaleType.FIT_XY
        }

        // Pattern dot colors & emojis
        binding.applyThemePatternView.setPatternColors(
            theme.normalColor,
            theme.selectedColor,
            theme.lineColor
        )
        binding.applyThemePatternView.setPatternEmojis(
            theme.normalEmoji,
            theme.selectedEmoji
        )

        // Interactive preview only — clear after drawing
        binding.applyThemePatternView.setOnPatternListener(object : PatternLockView.OnPatternListener {
            override fun onPatternComplete(pattern: List<Int>) {
                Handler(Looper.getMainLooper()).postDelayed({
                    binding.applyThemePatternView.clearPattern()
                }, 800)
            }
        })
    }

    private fun setupListeners() {
        binding.btnBackApplyTheme.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnApplyTheme.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
            prefs.edit().putInt("selected_theme_id", themeId).apply()

            val theme = ThemeConfig.getById(themeId)
            Toast.makeText(context, "\"${theme.name}\" theme apply ho gaya! ✨", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
