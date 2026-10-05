package com.applockerapp.presentation.themes

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.applockerapp.R
import com.applockerapp.databinding.FragmentThemesBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ThemesFragment : Fragment() {

    private var _binding: FragmentThemesBinding? = null
    private val binding get() = _binding!!

    private lateinit var themeAdapter: ThemeAdapter
    private var appliedThemeId = 1
    private var activeTab = 0 // 0=Animals, 1=Sports, 2=Gradients, 3=Hot, 4=Emoji

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentThemesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadAppliedThemeId()
        setupRecyclerView()
        setupChips()
        setupListeners()
        
        // Restore the active tab and check the corresponding chip
        val chipId = when (activeTab) {
            0 -> R.id.chipAnimals
            1 -> R.id.chipSports
            2 -> R.id.chipGradients
            3 -> R.id.chipTrending
            4 -> R.id.chipEmoji
            else -> R.id.chipAnimals
        }
        binding.chipGroupThemes.check(chipId)
        loadTab(activeTab)
    }

    override fun onResume() {
        super.onResume()
        loadAppliedThemeId()
        themeAdapter.updateItems(getItemsForTab(activeTab), appliedThemeId)
    }

    private fun loadAppliedThemeId() {
        val prefs = requireContext().getSharedPreferences("applock_prefs", Context.MODE_PRIVATE)
        appliedThemeId = prefs.getInt("selected_theme_id", 1)
    }

    private fun setupRecyclerView() {
        themeAdapter = ThemeAdapter(emptyList(), appliedThemeId) { themeItem ->
            // Navigate to preview with chosen theme ID
            val bundle = Bundle().apply { putInt("themeId", themeItem.id) }
            findNavController().navigate(R.id.action_themesFragment_to_applyThemeFragment, bundle)
        }
        binding.rvThemes.apply {
            layoutManager = GridLayoutManager(requireContext(), 2)
            adapter = themeAdapter
        }
    }
    private fun setupChips() {
        binding.chipAnimals.setOnClickListener   { loadTab(0) }
        binding.chipSports.setOnClickListener    { loadTab(1) }
        binding.chipGradients.setOnClickListener { loadTab(2) }
        binding.chipTrending.setOnClickListener  { loadTab(3) }
        binding.chipEmoji.setOnClickListener     { loadTab(4) }
    }

    private fun setupListeners() {
        binding.btnBackThemes.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun loadTab(tab: Int) {
        activeTab = tab
        themeAdapter.updateItems(getItemsForTab(tab), appliedThemeId)
        binding.rvThemes.scrollToPosition(0)
    }

    /** Convert ThemeConfig.Theme list to ThemeItem list for the adapter */
    private fun getItemsForTab(tab: Int): List<ThemeItem> {
        val ids: Iterable<Int> = when (tab) {
            0 -> (1..10).toList() + listOf(61, 62)  // Animals + Markhor + Penguin
            1 -> (51..60).toList() + listOf(63, 64)  // Sports + Canelo + Curry
            2 -> 31..40  // Gradients
            3 -> 41..50  // Hot
            4 -> 21..30  // Emoji
            else -> 1..10
        }
        return ids.map { id ->
            val t = ThemeConfig.getById(id)
            ThemeItem(
                id         = t.id,
                name       = t.name,
                drawableRes = t.drawableRes,
                isImage    = t.isImage,
                isPro      = false  // All themes are FREE
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
