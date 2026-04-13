package com.google.mediapipe.examples.poselandmarker.fragment

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.google.mediapipe.examples.poselandmarker.BluetoothManager
import com.google.mediapipe.examples.poselandmarker.MainViewModel
import com.google.mediapipe.examples.poselandmarker.PoseLandmarkerHelper
import com.google.mediapipe.examples.poselandmarker.PoseReference
import com.google.mediapipe.examples.poselandmarker.PoseReferenceAdapter
import com.google.mediapipe.examples.poselandmarker.PoseReferenceMatcher
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentGalleryBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import kotlinx.coroutines.launch
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.mediapipe.examples.poselandmarker.BluetoothViewModel

class GalleryFragment : Fragment(), PoseLandmarkerHelper.LandmarkerListener {
    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null
    private val fragmentGalleryBinding
        get() = _fragmentGalleryBinding!!
    private lateinit var poseLandmarkerHelper: PoseLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private val bluetoothViewModel: BluetoothViewModel by activityViewModels()
    private val bluetoothManager get() = bluetoothViewModel.bluetoothManager
    private val poseReferenceAdapter = PoseReferenceAdapter()

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ScheduledExecutorService

    private val addPoseImages =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            if (uris.isEmpty()) {
                return@registerForActivityResult
            }

            val remainingSlots =
                PoseLandmarkerHelper.MAX_NUM_POSE_REFERENCES - viewModel.poseReferences.value.size
            if (remainingSlots <= 0) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.pose_reference_limit_reached),
                    Toast.LENGTH_LONG
                ).show()
                return@registerForActivityResult
            }

            val selectedUris = uris.take(remainingSlots)
            selectedUris.forEach(::persistReadPermission)
            processPoseReferenceUris(selectedUris)

            if (uris.size > remainingSlots) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.pose_reference_slots_used, remainingSlots),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding = FragmentGalleryBinding.inflate(inflater, container, false)
        return fragmentGalleryBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        fragmentGalleryBinding.poseReferenceList.apply {
            layoutManager = GridLayoutManager(requireContext(), 3)
            adapter = poseReferenceAdapter
        }
        poseReferenceAdapter.onDeleteReference = { reference ->
            viewModel.removePoseReference(reference.id)
        }

        fragmentGalleryBinding.fabGetContent.setOnClickListener {
            addPoseImages.launch(arrayOf("image/*"))
        }



        fragmentGalleryBinding.btnBluetooth.setOnClickListener {
            if (!bluetoothManager.isBluetoothSupported()) {
                Toast.makeText(requireContext(), "Bluetooth not supported", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!bluetoothManager.isBluetoothEnabled()) {
                Toast.makeText(requireContext(), "Turn Bluetooth ON first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val connectGranted = ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

                val scanGranted = ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED

                if (!connectGranted || !scanGranted) {
                    requestBluetoothPermissions.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                        )
                    )
                    return@setOnClickListener
                }
            }

            showBluetoothDevicePicker()
        }

        initBottomSheetControls()
        observeBluetoothStatus()
        observePoseReferences()
    }

    override fun onDestroyView() {
        _fragmentGalleryBinding = null
        super.onDestroyView()
    }

    override fun onDestroy() {
        if (this::backgroundExecutor.isInitialized) {
            backgroundExecutor.shutdown()
        }
        super.onDestroy()
    }

    private fun observeBluetoothStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            bluetoothManager.status.collect { status ->
                when (status) {
                    is BluetoothManager.Status.IDLE -> {
                        fragmentGalleryBinding.btnBluetooth.text = "Bluetooth Pairing"
                        fragmentGalleryBinding.btnBluetooth.isEnabled = true
                    }

                    is BluetoothManager.Status.CONNECTING -> {
                        fragmentGalleryBinding.btnBluetooth.text = "Connecting..."
                        fragmentGalleryBinding.btnBluetooth.isEnabled = false
                    }

                    is BluetoothManager.Status.CONNECTED -> {
                        fragmentGalleryBinding.btnBluetooth.text =
                            "Connected: ${status.deviceName ?: "ESP32"}"
                        fragmentGalleryBinding.btnBluetooth.isEnabled = true

                        Toast.makeText(
                            requireContext(),
                            "Connected to ${status.deviceName ?: status.address}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    is BluetoothManager.Status.ERROR -> {
                        fragmentGalleryBinding.btnBluetooth.text = "Bluetooth Pairing"
                        fragmentGalleryBinding.btnBluetooth.isEnabled = true

                        Toast.makeText(
                            requireContext(),
                            status.message,
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private val requestBluetoothPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val connectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
            val scanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] == true

            if (connectGranted && scanGranted) {
                showBluetoothDevicePicker()
            } else {
                Toast.makeText(
                    requireContext(),
                    "Bluetooth permissions are required to connect",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private fun observePoseReferences() {
        lifecycleScope.launch {
            viewModel.poseReferences.collect { references ->
                poseReferenceAdapter.submitList(references)
                fragmentGalleryBinding.tvPlaceholder.visibility =
                    if (references.isEmpty()) View.VISIBLE else View.GONE
                fragmentGalleryBinding.poseReferenceCount.text = getString(
                    R.string.pose_reference_total,
                    references.size,
                    PoseLandmarkerHelper.MAX_NUM_POSE_REFERENCES
                )
            }
        }
    }

    private fun initBottomSheetControls() {
        fragmentGalleryBinding.bottomSheetLayout.maxPosesValue.text =
            viewModel.currentMaxPoses.toString()
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence)
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinPoseTrackingConfidence)
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence)

        fragmentGalleryBinding.bottomSheetLayout.maxPosesMinus.setOnClickListener {
            if (viewModel.currentMaxPoses > 1) {
                viewModel.setMaxPoses(viewModel.currentMaxPoses - 1)
                updateControlsUi()
            }
        }

        fragmentGalleryBinding.bottomSheetLayout.maxPosesPlus.setOnClickListener {
            if (viewModel.currentMaxPoses < PoseLandmarkerHelper.MAX_NUM_POSES) {
                viewModel.setMaxPoses(viewModel.currentMaxPoses + 1)
                updateControlsUi()
            }
        }

        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPoseDetectionConfidence >= 0.2) {
                viewModel.setMinPoseDetectionConfidence(
                    viewModel.currentMinPoseDetectionConfidence - 0.1f
                )
                updateControlsUi()
            }
        }

        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPoseDetectionConfidence <= 0.8) {
                viewModel.setMinPoseDetectionConfidence(
                    viewModel.currentMinPoseDetectionConfidence + 0.1f
                )
                updateControlsUi()
            }
        }

        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPoseTrackingConfidence >= 0.2) {
                viewModel.setMinPoseTrackingConfidence(
                    viewModel.currentMinPoseTrackingConfidence - 0.1f
                )
                updateControlsUi()
            }
        }

        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPoseTrackingConfidence <= 0.8) {
                viewModel.setMinPoseTrackingConfidence(
                    viewModel.currentMinPoseTrackingConfidence + 0.1f
                )
                updateControlsUi()
            }
        }

        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPosePresenceConfidence >= 0.2) {
                viewModel.setMinPosePresenceConfidence(
                    viewModel.currentMinPosePresenceConfidence - 0.1f
                )
                updateControlsUi()
            }
        }

        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPosePresenceConfidence <= 0.8) {
                viewModel.setMinPosePresenceConfidence(
                    viewModel.currentMinPosePresenceConfidence + 0.1f
                )
                updateControlsUi()
            }
        }

        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate,
            false
        )
        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    viewModel.setDelegate(position)
                    updateControlsUi()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }

        fragmentGalleryBinding.bottomSheetLayout.spinnerModel.setSelection(
            viewModel.currentModel,
            false
        )
        fragmentGalleryBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    viewModel.setModel(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
    }

    private fun updateControlsUi() {
        fragmentGalleryBinding.bottomSheetLayout.maxPosesValue.text =
            viewModel.currentMaxPoses.toString()
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence)
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinPoseTrackingConfidence)
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdValue.text =
            String.format(Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence)
    }

    private fun processPoseReferenceUris(uris: List<Uri>) {
        setUiEnabled(false)
        fragmentGalleryBinding.progress.visibility = View.VISIBLE

        backgroundExecutor.execute {
            poseLandmarkerHelper =
                PoseLandmarkerHelper(
                    context = requireContext(),
                    runningMode = RunningMode.IMAGE,
                    maxPoses = viewModel.currentMaxPoses,
                    currentModel = viewModel.currentModel,
                    minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                    minPoseTrackingConfidence = viewModel.currentMinPoseTrackingConfidence,
                    minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                    currentDelegate = viewModel.currentDelegate
                )

            val references = mutableListOf<PoseReference>()
            var duplicateCount = 0
            val existingReferences = viewModel.poseReferences.value

            uris.forEachIndexed { index, uri ->
                val bitmap = loadBitmapFromUri(uri) ?: return@forEachIndexed
                val resultBundle = poseLandmarkerHelper.detectImage(bitmap) ?: return@forEachIndexed
                val poseResult = resultBundle.results.firstOrNull() ?: return@forEachIndexed
                if (poseResult.landmarks().isEmpty()) {
                    return@forEachIndexed
                }
                val embedding = PoseReferenceMatcher.createEmbedding(
                    poseResult.landmarks().first()
                )

                if (PoseReferenceMatcher.isDuplicatePose(existingReferences + references, embedding)) {
                    duplicateCount += 1
                    return@forEachIndexed
                }

                references += PoseReference(
                    id = System.currentTimeMillis() + index,
                    name = "",
                    uriString = uri.toString(),
                    detectedPoseCount = poseResult.landmarks().size,
                    inferenceTimeMs = resultBundle.inferenceTime,
                    landmarkEmbedding = embedding
                )
            }

            poseLandmarkerHelper.clearPoseLandmarker()

            activity?.runOnUiThread {
                viewModel.addPoseReferences(references)
                fragmentGalleryBinding.progress.visibility = View.GONE
                setUiEnabled(true)

                if (references.isEmpty()) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.pose_reference_no_pose_found),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(
                            R.string.pose_reference_added_with_skips,
                            references.size,
                            duplicateCount
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(requireActivity().contentResolver, uri)
            ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true)
        } else {
            MediaStore.Images.Media.getBitmap(requireActivity().contentResolver, uri)
                ?.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    private fun persistReadPermission(uri: Uri) {
        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some picker implementations only provide an in-memory grant.
        }
    }

    private fun setUiEnabled(enabled: Boolean) {
        fragmentGalleryBinding.fabGetContent.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.maxPosesMinus.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.maxPosesPlus.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdMinus.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.detectionThresholdPlus.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdMinus.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.trackingThresholdPlus.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdMinus.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.presenceThresholdPlus.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.isEnabled = enabled
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            fragmentGalleryBinding.progress.visibility = View.GONE
            setUiEnabled(true)
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == PoseLandmarkerHelper.GPU_ERROR) {
                fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    PoseLandmarkerHelper.DELEGATE_CPU,
                    false
                )
            }
        }
    }

    override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) = Unit

    private fun showBluetoothDevicePicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            val scanGranted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            if (!connectGranted || !scanGranted) {
                Toast.makeText(
                    requireContext(),
                    "Bluetooth permissions missing",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
        }

        try {
            val devices = bluetoothManager.getPairedDevices()
            if (devices.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "No paired Bluetooth devices", Toast.LENGTH_SHORT).show()
                return
            }

            val deviceList = devices.toList()
            val deviceNames = deviceList.map {
                "${it.name ?: "Unknown device"} (${it.address})"
            }.toTypedArray()

            AlertDialog.Builder(requireContext())
                .setTitle("Select ESP32 Device")
                .setItems(deviceNames) { _, which ->
                    val selectedDevice = deviceList[which]
                    bluetoothManager.connect(selectedDevice.address)
                }
                .show()
        } catch (e: SecurityException) {
            Toast.makeText(
                requireContext(),
                "Bluetooth permission denied: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}