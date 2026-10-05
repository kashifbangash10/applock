package com.applockerapp.presentation.search

import android.animation.ValueAnimator
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.applockerapp.R
import com.applockerapp.databinding.DialogLockAppBinding
import com.applockerapp.databinding.FragmentSearchBinding
import com.applockerapp.presentation.home.AppAdapter
import com.applockerapp.presentation.home.AppItem
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by viewModels()
    private lateinit var appAdapter: AppAdapter
    private var focusAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupListeners()
        observeUiState()

        _binding?.etSearch?.requestFocus()
        _binding?.etSearch?.postDelayed({
            if (isAdded && _binding != null) {
                val ctx = context ?: return@postDelayed
                val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                _binding?.etSearch?.let { et ->
                    imm?.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }, 150L)
    }

    override fun onResume() {
        super.onResume()
        // Refresh lock state in case user modified it in home screen
        viewModel.refreshLockStates()
    }

    private fun setupRecyclerView() {
        appAdapter = AppAdapter { appItem ->
            showLockDialog(appItem)
        }
        val ctx = context ?: return
        binding.rvSearchApps.apply {
            layoutManager = LinearLayoutManager(ctx)
            adapter = appAdapter
        }
    }

    private fun setupListeners() {
        binding.btnBackSearch.setOnClickListener {
            if (isAdded) {
                try {
                    findNavController().popBackStack()
                } catch (e: Exception) {
                    // Guard against rapid click destination collision
                }
            }
        }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }

        binding.etSearch.setOnFocusChangeListener { _, hasFocus ->
            if (!isAdded || _binding == null) return@setOnFocusChangeListener
            val ctx = context ?: return@setOnFocusChangeListener
            val density = ctx.resources.displayMetrics.density
            val targetStrokeWidth = if (hasFocus) {
                (3 * density).toInt()
            } else {
                (1.5f * density).toInt()
            }

            focusAnimator?.cancel()
            focusAnimator = ValueAnimator.ofInt(binding.cardSearchInput.strokeWidth, targetStrokeWidth).apply {
                duration = 200
                addUpdateListener { animator ->
                    if (_binding != null) {
                        binding.cardSearchInput.strokeWidth = animator.animatedValue as Int
                    }
                }
                start()
            }
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (_binding == null) return
                val text = s?.toString().orEmpty()
                binding.btnClearSearch.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                viewModel.setSearchQuery(text)
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun showLockDialog(appItem: AppItem) {
        val ctx = context ?: return
        val dialog = BottomSheetDialog(ctx, R.style.LockDialogTheme)
        val dialogBinding = DialogLockAppBinding.inflate(LayoutInflater.from(ctx))
        dialog.setContentView(dialogBinding.root)

        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)

        try {
            val pm = ctx.packageManager
            val icon: Drawable = pm.getApplicationIcon(appItem.packageName)
            dialogBinding.dialogAppIcon.setImageDrawable(icon)
        } catch (e: Exception) {
            dialogBinding.dialogAppIcon.setImageResource(appItem.iconDrawableId)
        }

        val isCurrentlyLocked = appItem.isLocked

        if (isCurrentlyLocked) {
            dialogBinding.dialogTitle.text = "Unlock ${appItem.appName}"
            dialogBinding.dialogDescription.text =
                "Unlock \"${appItem.appName}\" to allow access to this app."
            dialogBinding.btnLockAction.text = "Unlock"
            dialogBinding.btnLockAction.setIconResource(R.drawable.ic_lock_open)
            dialogBinding.btnLockAction.backgroundTintList =
                ContextCompat.getColorStateList(ctx, R.color.accent_color)
            dialogBinding.dialogLockBadge.setCardBackgroundColor(
                ContextCompat.getColor(ctx, R.color.accent_color)
            )
            dialogBinding.dialogLockBadgeIcon.setImageResource(R.drawable.ic_lock_closed)
        } else {
            dialogBinding.dialogTitle.text = "Lock ${appItem.appName}"
            dialogBinding.dialogDescription.text =
                "Lock \"${appItem.appName}\" to protect your privacy."
            dialogBinding.btnLockAction.text = "Lock"
            dialogBinding.btnLockAction.setIconResource(R.drawable.ic_lock_closed)
            dialogBinding.btnLockAction.backgroundTintList =
                ContextCompat.getColorStateList(ctx, R.color.primary)
            dialogBinding.dialogLockBadge.setCardBackgroundColor(
                ContextCompat.getColor(ctx, R.color.primary)
            )
            dialogBinding.dialogLockBadgeIcon.setImageResource(R.drawable.ic_lock_open)
        }

        dialogBinding.btnLockAction.setOnClickListener {
            viewModel.toggleAppLock(appItem)
            dialog.dismiss()
            val message = if (isCurrentlyLocked)
                "${appItem.appName} unlocked ✅"
            else
                "${appItem.appName} locked 🔒"
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }

        dialogBinding.tvNotNow.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    renderState(state)
                }
            }
        }
    }

    private fun renderState(state: SearchUiState) {
        if (_binding == null) return
        when (state) {
            is SearchUiState.Loading -> {
                binding.searchProgressBar.visibility = View.VISIBLE
                binding.rvSearchApps.visibility = View.GONE
                binding.layoutSearchEmptyState.visibility = View.GONE
            }
            is SearchUiState.Success -> {
                binding.searchProgressBar.visibility = View.GONE
                // Set visibility BEFORE submitList — DiffUtil callback may never fire
                // if items haven't changed (e.g. on refresh with same data)
                if (state.apps.isNotEmpty()) {
                    binding.rvSearchApps.visibility = View.VISIBLE
                    binding.layoutSearchEmptyState.visibility = View.GONE
                } else {
                    binding.rvSearchApps.visibility = View.GONE
                    binding.layoutSearchEmptyState.visibility = View.VISIBLE
                }
                appAdapter.submitList(state.apps)
            }
            is SearchUiState.Error -> {
                binding.searchProgressBar.visibility = View.GONE
                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        focusAnimator?.cancel()
        focusAnimator = null
        super.onDestroyView()
        _binding = null
    }
}
