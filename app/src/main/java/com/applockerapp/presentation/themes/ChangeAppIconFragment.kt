package com.applockerapp.presentation.themes

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.applockerapp.R
import com.applockerapp.databinding.FragmentChangeAppIconBinding
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint

import com.google.android.material.snackbar.Snackbar

@AndroidEntryPoint
class ChangeAppIconFragment : Fragment() {

    private var _binding: FragmentChangeAppIconBinding? = null
    private val binding get() = _binding!!

    private var selectedKey: String = "default"

    // Map key -> (aliasName, iconRes, bgColor)
    data class IconOption(
        val key: String,
        val aliasName: String,
        val iconRes: Int,
        val bgColor: Int,
        val view: MaterialCardView
    )

    private lateinit var allOptions: List<IconOption>
    private val primaryColor get() = ContextCompat.getColor(requireContext(), R.color.primary)

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangeAppIconBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Load saved selection
        val prefs = requireContext().getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
        selectedKey = prefs.getString("selected_app_icon_key", "default") ?: "default"

        // Build options list mapping each card view
        allOptions = listOf(
            IconOption("calc_teal",    "com.applockerapp.IconCalcTeal",    R.drawable.ic_fg_calc, Color.parseColor("#FF00897B"), binding.choiceCalcTeal),
            IconOption("calc_purple",  "com.applockerapp.IconCalcPurple",  R.drawable.ic_fg_calc, Color.parseColor("#FF7B1FA2"), binding.choiceCalcPurple),
            IconOption("calc_blue",    "com.applockerapp.IconCalcBlue",    R.drawable.ic_fg_calc, Color.parseColor("#FF1976D2"), binding.choiceCalcBlue),
            IconOption("calc_pink",    "com.applockerapp.IconCalcPink",    R.drawable.ic_fg_calc, Color.parseColor("#FFE91E63"), binding.choiceCalcPink),

            IconOption("weather_blue",   "com.applockerapp.IconWeatherBlue",   R.drawable.ic_fg_weather, Color.parseColor("#FF0288D1"), binding.choiceWeatherBlue),
            IconOption("weather_purple", "com.applockerapp.IconWeatherPurple", R.drawable.ic_fg_weather, Color.parseColor("#FF512DA8"), binding.choiceWeatherPurple),
            IconOption("weather_green",  "com.applockerapp.IconWeatherGreen",  R.drawable.ic_fg_weather, Color.parseColor("#FF2E7D32"), binding.choiceWeatherGreen),
            IconOption("weather_cyan",   "com.applockerapp.IconWeatherCyan",   R.drawable.ic_fg_weather, Color.parseColor("#FF00838F"), binding.choiceWeatherCyan),

            IconOption("rec_cyan",   "com.applockerapp.IconRecCyan",   R.drawable.ic_fg_recorder, Color.parseColor("#FF00ACC1"), binding.choiceRecCyan),
            IconOption("rec_violet", "com.applockerapp.IconRecViolet", R.drawable.ic_fg_recorder, Color.parseColor("#FF6200EE"), binding.choiceRecViolet),
            IconOption("rec_gold",   "com.applockerapp.IconRecGold",   R.drawable.ic_fg_recorder, Color.parseColor("#FFF9A825"), binding.choiceRecGold),
            IconOption("rec_orange", "com.applockerapp.IconRecOrange", R.drawable.ic_fg_recorder, Color.parseColor("#FFE64A19"), binding.choiceRecOrange),

            IconOption("music_pink",   "com.applockerapp.IconMusicPink",   R.drawable.ic_fg_music, Color.parseColor("#FFE91E63"), binding.choiceMusicPink),
            IconOption("music_green",  "com.applockerapp.IconMusicGreen",  R.drawable.ic_fg_music, Color.parseColor("#FF388E3C"), binding.choiceMusicGreen),
            IconOption("music_indigo", "com.applockerapp.IconMusicIndigo", R.drawable.ic_fg_music, Color.parseColor("#FF303F9F"), binding.choiceMusicIndigo),
            IconOption("music_amber",  "com.applockerapp.IconMusicAmber",  R.drawable.ic_fg_music, Color.parseColor("#FFFFA000"), binding.choiceMusicAmber)
        )

        updateCurrentIconCard()
        setupClickListeners()
        refreshSelectionUI()

        binding.btnBackChangeIcon.setOnClickListener { findNavController().popBackStack() }

        binding.btnResetIcon.setOnClickListener {
            selectedKey = "default"
            applyIconAlias()
            updateCurrentIconCard()
            refreshSelectionUI()
            val rootView = activity?.findViewById<View>(android.R.id.content) ?: binding.root
            Snackbar.make(rootView, "App icon reset to default ✨", Snackbar.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }

        binding.btnApplyIcon.setOnClickListener {
            applyIconAlias()
            val label = selectedKey.split("_")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
            val displayName = if (selectedKey == "default") "Default" else label
            val rootView = activity?.findViewById<View>(android.R.id.content) ?: binding.root
            Snackbar.make(rootView, "Icon changed to \"$displayName\" — check home screen! ✨", Snackbar.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }

    private fun setupClickListeners() {
        allOptions.forEach { option ->
            option.view.setOnClickListener {
                option.view.animate()
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(80)
                    .withEndAction {
                        option.view.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(120)
                            .start()
                    }.start()
                selectedKey = option.key
                updateCurrentIconCard()
                refreshSelectionUI()
            }
        }
    }

    private fun refreshSelectionUI() {
        val strokeActive = primaryColor
        allOptions.forEach { option ->
            if (option.key == selectedKey) {
                option.view.strokeColor = strokeActive
                option.view.strokeWidth = 9
                option.view.cardElevation = 10f
            } else {
                option.view.strokeColor = Color.TRANSPARENT
                option.view.strokeWidth = 0
                option.view.cardElevation = 4f
            }
        }
    }

    private fun updateCurrentIconCard() {
        if (selectedKey == "default") {
            // Show the actual default app launcher icon
            binding.cardCurrentIcon.setCardBackgroundColor(Color.parseColor("#FF0D47A1")) // Dark navy — matches real launcher bg
            binding.imgCurrentIcon.setImageResource(R.drawable.ic_launcher_default_premium)
            binding.imgCurrentIcon.imageTintList = null
        } else {
            val opt = allOptions.firstOrNull { it.key == selectedKey }
            if (opt != null) {
                binding.cardCurrentIcon.setCardBackgroundColor(opt.bgColor)
                binding.imgCurrentIcon.setImageResource(opt.iconRes)
                binding.imgCurrentIcon.imageTintList = null // Clear tint so colored icons render correctly
            }
        }
    }

    private fun applyIconAlias() {
        val pm = requireContext().packageManager
        val pkg = requireContext().packageName

        // Save preference
        requireContext().getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
            .edit().putString("selected_app_icon_key", selectedKey).apply()

        val defaultAlias   = ComponentName(pkg, "com.applockerapp.IconDefault")
        val selectedAlias  = allOptions.firstOrNull { it.key == selectedKey }
            ?.let { ComponentName(pkg, it.aliasName) }

        try {
            // Disable all aliases first
            val allAliases = listOf(
                "com.applockerapp.IconDefault",
                "com.applockerapp.IconCalcTeal", "com.applockerapp.IconCalcPurple", "com.applockerapp.IconCalcBlue", "com.applockerapp.IconCalcPink",
                "com.applockerapp.IconWeatherBlue", "com.applockerapp.IconWeatherPurple", "com.applockerapp.IconWeatherGreen", "com.applockerapp.IconWeatherCyan",
                "com.applockerapp.IconRecCyan", "com.applockerapp.IconRecViolet", "com.applockerapp.IconRecGold", "com.applockerapp.IconRecOrange",
                "com.applockerapp.IconMusicPink", "com.applockerapp.IconMusicGreen", "com.applockerapp.IconMusicIndigo", "com.applockerapp.IconMusicAmber"
            )
            allAliases.forEach { alias ->
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, alias),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }

            // Enable selected alias
            val toEnable = if (selectedKey == "default") defaultAlias else selectedAlias
            toEnable?.let {
                pm.setComponentEnabledSetting(
                    it,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    0 // Using 0 instead of DONT_KILL_APP forces the OS launcher to refresh and show the new icon immediately by restarting the app process
                )
            }
        } catch (e: Exception) {
            Log.e("ChangeAppIcon", "Failed to switch activity alias", e)
            Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
