package com.google.mediapipe.examples.poselandmarker.fragment

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.mediapipe.examples.poselandmarker.MainViewModel
import com.google.mediapipe.examples.poselandmarker.R
import com.google.mediapipe.examples.poselandmarker.databinding.FragmentAudioPasswordBinding
import java.io.File
import java.util.Locale
import kotlin.math.log10
import kotlin.math.roundToInt

class AudioPasswordFragment : Fragment() {

    private var _binding: FragmentAudioPasswordBinding? = null
    private val binding
        get() = _binding!!

    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isRecording = false
    private var speechRecognizer: SpeechRecognizer? = null
    private var pendingRecognizedPhrase: String? = null
    private val levelHandler = Handler(Looper.getMainLooper())
    private val viewModel: MainViewModel by activityViewModels()
    private val levelUpdater = object : Runnable {
        override fun run() {
            val recorder = mediaRecorder
            if (!isRecording || recorder == null || _binding == null) {
                return
            }

            val amplitude = try {
                recorder.maxAmplitude
            } catch (_: IllegalStateException) {
                0
            }
            val normalizedLevel = if (amplitude > 0) {
                ((log10(amplitude.toDouble()) / log10(32767.0)) * 100.0)
                    .coerceIn(0.0, 100.0)
                    .roundToInt()
            } else {
                0
            }

            binding.audioLevelIndicator.progress = normalizedLevel
            binding.tvAudioLevel.text = getString(
                R.string.audio_password_level_format,
                normalizedLevel
            )
            levelHandler.postDelayed(this, AUDIO_LEVEL_REFRESH_MS)
        }
    }

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startRecording()
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.audio_password_permission_denied),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAudioPasswordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRecordAudioPassword.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                ensureAudioPermissionAndRecord()
            }
        }

        binding.btnPlayAudioPassword.setOnClickListener {
            playAudioPassword()
        }

        updateUi()
    }

    override fun onStop() {
        super.onStop()
        if (isRecording) {
            stopRecording()
        }
        releasePlayer()
    }

    override fun onDestroyView() {
        releaseRecorder()
        releasePlayer()
        speechRecognizer?.destroy()
        speechRecognizer = null
        _binding = null
        super.onDestroyView()
    }

    private fun ensureAudioPermissionAndRecord() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startRecording()
        } else {
            requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        releasePlayer()
        releaseRecorder()
        startSpeechRecognition()
        pendingRecognizedPhrase = null

        try {
            val outputFile = audioPasswordFile()
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(requireContext())
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            updateUi()
            startLevelUpdates()
            Toast.makeText(
                requireContext(),
                getString(R.string.audio_password_recording_started),
                Toast.LENGTH_SHORT
            ).show()
        } catch (exception: Exception) {
            speechRecognizer?.cancel()
            releaseRecorder()
            isRecording = false
            stopLevelUpdates()
            updateUi()
            Toast.makeText(
                requireContext(),
                getString(R.string.audio_password_recording_failed, exception.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun stopRecording() {
        try {
            speechRecognizer?.stopListening()
            mediaRecorder?.stop()
        } catch (_: RuntimeException) {
            audioPasswordFile().delete()
        } finally {
            stopLevelUpdates()
            releaseRecorder()
            isRecording = false
            updateUi()
        }

        val recognizedPhrase = pendingRecognizedPhrase
        val hasAtLeastOneWord = !recognizedPhrase.isNullOrBlank()
        if (hasAtLeastOneWord) {
            viewModel.setAudioPasswordPhrase(recognizedPhrase)
            updateUi()
        } else {
            audioPasswordFile().delete()
        }

        Toast.makeText(
            requireContext(),
            if (hasAtLeastOneWord) {
                getString(R.string.audio_password_recording_saved)
            } else {
                getString(R.string.audio_password_invalid_input)
            },
            if (hasAtLeastOneWord) Toast.LENGTH_SHORT else Toast.LENGTH_LONG
        ).show()
    }

    private fun playAudioPassword() {
        val audioFile = audioPasswordFile()
        if (!audioFile.exists()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.audio_password_none_saved),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        releasePlayer()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                setOnCompletionListener {
                    releasePlayer()
                    updateUi()
                }
                prepare()
                start()
            }
            updateUi()
        } catch (exception: Exception) {
            releasePlayer()
            Toast.makeText(
                requireContext(),
                getString(R.string.audio_password_playback_failed, exception.message ?: ""),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updateUi() {
        if (_binding == null) {
            return
        }

        val hasSavedAudio = audioPasswordFile().exists()
        binding.btnRecordAudioPassword.setImageResource(
            if (isRecording) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_btn_speak_now
        )
        binding.tvAudioPasswordStatus.text = when {
            isRecording -> getString(R.string.audio_password_status_recording)
            hasSavedAudio && !viewModel.audioPasswordPhrase.value.isNullOrBlank() ->
                getString(R.string.audio_password_status_saved_phrase, viewModel.audioPasswordPhrase.value)
            hasSavedAudio -> getString(R.string.audio_password_status_saved)
            else -> getString(R.string.audio_password_status_empty)
        }
        binding.btnPlayAudioPassword.isEnabled = hasSavedAudio && !isRecording
        binding.audioLevelIndicator.visibility = if (isRecording) View.VISIBLE else View.INVISIBLE
        binding.tvAudioLevel.visibility = if (isRecording) View.VISIBLE else View.INVISIBLE
        if (!isRecording) {
            binding.audioLevelIndicator.progress = 0
            binding.tvAudioLevel.text = getString(R.string.audio_password_level_idle)
        }
    }

    private fun audioPasswordFile(): File {
        return File(requireContext().filesDir, AUDIO_PASSWORD_FILENAME)
    }

    private fun releaseRecorder() {
        mediaRecorder?.release()
        mediaRecorder = null
    }

    private fun releasePlayer() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun startSpeechRecognition() {
        pendingRecognizedPhrase = null
        if (!SpeechRecognizer.isRecognitionAvailable(requireContext())) {
            return
        }

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(requireContext()).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit

                override fun onBeginningOfSpeech() = Unit

                override fun onRmsChanged(rmsdB: Float) = Unit

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) = Unit

                override fun onResults(results: Bundle?) {
                    pendingRecognizedPhrase = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let(::normalizePhrase)
                    if (!pendingRecognizedPhrase.isNullOrBlank()) {
                        viewModel.setAudioPasswordPhrase(pendingRecognizedPhrase)
                        updateUi()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) = Unit

                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }

        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        speechRecognizer?.startListening(intent)
    }

    private fun startLevelUpdates() {
        binding.audioLevelIndicator.progress = 0
        binding.tvAudioLevel.text = getString(R.string.audio_password_level_format, 0)
        levelHandler.removeCallbacks(levelUpdater)
        levelHandler.post(levelUpdater)
    }

    private fun stopLevelUpdates() {
        levelHandler.removeCallbacks(levelUpdater)
    }

    companion object {
        private const val AUDIO_PASSWORD_FILENAME = "audio_password.m4a"
        private const val AUDIO_LEVEL_REFRESH_MS = 150L
    }

    private fun normalizePhrase(value: String): String {
        return value
            .lowercase(Locale.US)
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}
