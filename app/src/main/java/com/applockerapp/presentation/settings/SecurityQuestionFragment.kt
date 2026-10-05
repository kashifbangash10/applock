package com.applockerapp.presentation.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.applockerapp.R
import com.applockerapp.databinding.FragmentSecurityQuestionBinding
import com.applockerapp.utils.LockSettingsManager
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SecurityQuestionFragment : Fragment() {

    private var _binding: FragmentSecurityQuestionBinding? = null
    private val binding get() = _binding!!
    private lateinit var lockSettingsManager: LockSettingsManager

    private val questions = listOf(
        "What is your date of birth?",
        "What is your pet's name?",
        "What is your favorite school teacher's name?"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSecurityQuestionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lockSettingsManager = LockSettingsManager(requireContext())

        setupSpinner()
        loadExistingData()
        setupListeners()
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item, questions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerQuestions.adapter = adapter
    }

    private fun loadExistingData() {
        val index = lockSettingsManager.getSecurityQuestionIndex()
        if (index in questions.indices) {
            binding.spinnerQuestions.setSelection(index)
        }
        
        if (lockSettingsManager.isSecurityQuestionSet()) {
            binding.etAnswerInput.setHint("Answer is already set (Type to change)")
        }
    }

    private fun setupListeners() {
        binding.btnBackSecurity.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnSaveSecurity.setOnClickListener {
            val selectedIndex = binding.spinnerQuestions.selectedItemPosition
            val answer = binding.etAnswerInput.text.toString().trim()

            if (answer.isEmpty()) {
                if (lockSettingsManager.isSecurityQuestionSet()) {
                    val savedHash = lockSettingsManager.getSecurityAnswer()
                    if (savedHash != null) {
                        lockSettingsManager.saveSecurityQuestionWithHash(selectedIndex, savedHash)
                    }
                    Toast.makeText(context, "Security Question updated! ✅", Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                } else {
                    Toast.makeText(context, "Please enter the answer", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }

            lockSettingsManager.saveSecurityQuestion(selectedIndex, answer)
            Toast.makeText(context, "Security Question saved successfully! ✅", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
