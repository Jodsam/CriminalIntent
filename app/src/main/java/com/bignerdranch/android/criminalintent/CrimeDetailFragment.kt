package com.bignerdranch.android.criminalintent

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.view.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bignerdranch.android.criminalintent.databinding.FragmentCrimeDetailBinding
import kotlinx.coroutines.launch
import java.util.*
import android.Manifest
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import com.bignerdranch.android.criminalintent.database.getScaledBitmap
import java.io.File

private const val DATE_FORMAT = "EEE, MMM, dd"

class CrimeDetailFragment : Fragment() {
    private var _binding: FragmentCrimeDetailBinding? = null
    private val binding
        get() = checkNotNull(_binding) {
            "Cannot access binding because it is null. Is the view visible?"
        }

    private val args: CrimeDetailFragmentArgs by navArgs()
    private val crimeDetailViewModel: CrimeDetailViewModel by viewModels {
        CrimeDetailViewModelFactory(args.crimeId)
    }
    private fun updatePhoto(photoFileName: String?) {
        if (binding.crimePhoto.tag != photoFileName) {
            val photoFile = photoFileName?.let {
                File(requireContext().applicationContext.filesDir, it)
            }
            if (photoFile?.exists() == true) {
                binding.crimePhoto.doOnLayout { measuredView ->
                    val scaledBitmap = getScaledBitmap(
                        photoFile.path,
                        measuredView.width,
                        measuredView.height
                    )
                    binding.crimePhoto.setImageBitmap(scaledBitmap)
                    binding.crimePhoto.tag = photoFileName
                }
            } else {
                binding.crimePhoto.setImageBitmap(null)
                binding.crimePhoto.tag = null
            }
        }
    }

    private val selectSuspect = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let { parseContactSelection(it) }
    }
    private val takePhoto = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { didTakePhoto: Boolean ->
        if (didTakePhoto && photoName != null) {
            crimeDetailViewModel.updateCrime { oldCrime ->
                oldCrime.copy(photoFileName = photoName)
            }
        }
    }
    private var photoName: String? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentCrimeDetailBinding.inflate(layoutInflater, container, false)
        return binding.root
    }
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.apply {


            crimeCamera.setOnClickListener {
                val captureImageIntent = takePhoto.contract.createIntent(
                    requireContext(),
                    Uri.parse("")
                )
                crimeCamera.isEnabled = canResolveIntent(captureImageIntent)

                photoName = "IMG_${Date()}.JPG"
                val photoFile = File(requireContext().applicationContext.filesDir,
                    photoName)
                val photoUri = FileProvider.getUriForFile(
                    requireContext(),
                    "com.bignerdranch.android.criminalintent.fileprovider",
                    photoFile
                )
                takePhoto.launch(photoUri)

            }


            crimeCall.setOnClickListener {
                requestContactsPermission()
            }

            crimeSuspect.setOnClickListener {
                selectSuspect.launch(null)
            }

            val selectSuspectIntent = selectSuspect.contract.createIntent(
                requireContext(),
                null
            )
            crimeSuspect.isEnabled = canResolveIntent(selectSuspectIntent)

            crimeTitle.doOnTextChanged { text, _, _, _ ->
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(title = text.toString())
                }

            }

            crimeSolved.setOnCheckedChangeListener { _, isChecked ->
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(isSolved = isChecked)
                }

            }
        }


        val callback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.crimeTitle.text.isEmpty()) Toast.makeText(requireContext(), "Enter Title", Toast.LENGTH_LONG).show()
                else findNavController().popBackStack()
            }

        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                crimeDetailViewModel.crime.collect { crime ->
                    crime?.let { updateUi(it) }
                }
            }
        }
        setFragmentResultListener(
            DatePickerFragment.REQUEST_KEY_DATE
        ) { _, bundle ->
            val newDate =
                bundle.getSerializable(DatePickerFragment.BUNDLE_KEY_DATE) as Date
            crimeDetailViewModel.updateCrime { it.copy(date = newDate) }
        }

        setFragmentResultListener(
            TimePickerFragment.REQUEST_KEY_TIME
        ) { _, bundle ->
            val newDate =
                bundle.getSerializable(TimePickerFragment.BUNDLE_KEY_TIME) as Date
            crimeDetailViewModel.updateCrime { it.copy(date = newDate) }
        }




    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private fun updateUi(crime: Crime) {
        binding.apply {
            if (crimeTitle.text.toString() != crime.title) {
                crimeTitle.setText(crime.title)
            }
            crimeDate.text = crime.date.toString()
            crimeTime.text = DateFormat.format("HH:mm", crime.date)
            crimeSolved.isChecked = crime.isSolved

            crimeReport.setOnClickListener {
                val reportIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getCrimeReport(crime))
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        getString(R.string.crime_report_subject)
                    )
                }
                val chooserIntent = Intent.createChooser(
                    reportIntent,
                    getString(R.string.send_report)
                )
                startActivity(chooserIntent)

            }

            crimeSuspect.text = crime.suspect.ifEmpty {
                getString(R.string.crime_suspect_text)
            }

            crimeDate.setOnClickListener {
                findNavController().navigate(
                    CrimeDetailFragmentDirections.selectDate(crime.date)
                )
            }
            crimeTime.setOnClickListener {
                findNavController().navigate(CrimeDetailFragmentDirections.selectTime(crime.date))
            }
            updatePhoto(crime.photoFileName)

        }
    }
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.fragment_crime_detail, menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.delete_crime -> {
                viewLifecycleOwner.lifecycleScope.launch {
                    crimeDetailViewModel.crime.value?.let {
                            crime ->  crimeDetailViewModel.deleteCrime(crime = crime)
                        findNavController().popBackStack()
                    }

                }
                true
            }
            else -> false
        }
    }
    private fun getCrimeReport(crime: Crime): String {
        val solvedString = if (crime.isSolved) {
            getString(R.string.crime_report_solved)
        } else {
            getString(R.string.crime_report_unsolved)
        }
        val dateString = DateFormat.format(DATE_FORMAT, crime.date).toString()
        val suspectText = if (crime.suspect.isBlank()) {
            getString(R.string.crime_report_no_suspect)
        } else {
            getString(R.string.crime_report_suspect, crime.suspect)
        }
        return getString(
            R.string.crime_report,
            crime.title, dateString, solvedString, suspectText
        )
    }
    private fun parseContactSelection(contactUri: Uri) {
        val queryFields = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
        val queryCursor = requireActivity().contentResolver
            .query(contactUri, queryFields, null, null, null)
        queryCursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                val suspect = cursor.getString(0)
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(suspect = suspect)
                }
            }
        }
    }
    private fun canResolveIntent(intent: Intent): Boolean {
        val packageManager: PackageManager = requireActivity().packageManager
        val resolvedActivity: ResolveInfo? =
            packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
        return resolvedActivity != null
    }
    @SuppressLint("Range")
    private fun parseContactCall() {
        val phoneURI = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val queryFields = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val contactId = crimeDetailViewModel.crime.value?.suspect ?: return

        val queryCursor = requireActivity().contentResolver
            .query(phoneURI, queryFields, "${ContactsContract.Contacts.DISPLAY_NAME} = ?",
                arrayOf(contactId), null)

        queryCursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                val phoneNumber = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                makePhoneCall(phoneNumber)
            } else {
                Toast.makeText(requireContext(), "No phone number found for contact", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            Toast.makeText(requireContext(), "Failed to access contacts", Toast.LENGTH_SHORT).show()
        }
    }
    private fun makePhoneCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(
                requireActivity(),
                Manifest.permission.CALL_PHONE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CALL_PHONE),
                REQUEST_CALL_PHONE_PERMISSION

            )
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
            startActivity(dialIntent)
        }
    }
    private val REQUEST_READ_CONTACTS = 1
    private val REQUEST_CALL_PHONE_PERMISSION = 1


    private fun requestContactsPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), REQUEST_READ_CONTACTS)
        } else {
            // Permission has already been granted
            parseContactCall()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_READ_CONTACTS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission has been granted, parse contact call
                    parseContactCall()
                } else {
                    // Permission denied
                    Toast.makeText(requireContext(), "Permission denied to access contacts", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}