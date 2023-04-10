package com.bignerdranch.android.criminalintent


import android.app.AlertDialog
import android.app.Dialog
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window

import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs

import com.bignerdranch.android.criminalintent.databinding.FragmentPhotoDialogBinding
import java.io.File


class PhotoDialogFragment : DialogFragment() {
    private var _binding: FragmentPhotoDialogBinding? = null
    private val binding
        get() = checkNotNull(_binding) {
            "Cannot access binding because it is null. Is the view visible?"
        }
    private val args: PhotoDialogFragmentArgs by navArgs()
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = FragmentPhotoDialogBinding.inflate(LayoutInflater.from(context))
        val builder = AlertDialog.Builder(activity)
        builder.setView(binding.root)
        updatePhoto(args.photoName)
        binding.crimePhoto.setOnClickListener {
            findNavController().popBackStack()
        }
        getDialog()?.getWindow()?.requestFeature(Window.FEATURE_NO_TITLE);


        return builder.create()

    }
    private fun updatePhoto(photoFileName: String?) {
            val photoFile = photoFileName?.let {
                File(requireContext().applicationContext.filesDir, photoFileName)
            }
                    val scaledBitmap = BitmapFactory.decodeFile(photoFile?.absolutePath)
                    binding.crimePhoto.setImageBitmap(scaledBitmap)

   }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
