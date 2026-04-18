package com.google.mediapipe.examples.poselandmarker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 *  This ViewModel is used to store pose landmarker helper settings
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences =
        application.getSharedPreferences(PREFERENCE_NAME, android.content.Context.MODE_PRIVATE)

    private var _model = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL
    private var _delegate: Int = PoseLandmarkerHelper.DELEGATE_CPU
    private var _maxPoses: Int = PoseLandmarkerHelper.DEFAULT_NUM_POSES
    private var _minPoseDetectionConfidence: Float =
        PoseLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE
    private var _minPoseTrackingConfidence: Float = PoseLandmarkerHelper
        .DEFAULT_POSE_TRACKING_CONFIDENCE
    private var _minPosePresenceConfidence: Float = PoseLandmarkerHelper
        .DEFAULT_POSE_PRESENCE_CONFIDENCE
    private val _poseReferences = MutableStateFlow(loadPoseReferences())
    private val _passwordReferenceIds = MutableStateFlow(loadPasswordReferenceIds())
    private val _audioPasswordPhrase = MutableStateFlow(loadAudioPasswordPhrase())

    val currentDelegate: Int get() = _delegate
    val currentModel: Int get() = _model
    val currentMaxPoses: Int get() = _maxPoses
    val poseReferences: StateFlow<List<PoseReference>> = _poseReferences.asStateFlow()
    val passwordReferenceIds: StateFlow<List<Long>> = _passwordReferenceIds.asStateFlow()
    val audioPasswordPhrase: StateFlow<String?> = _audioPasswordPhrase.asStateFlow()
    val currentMinPoseDetectionConfidence: Float
        get() =
            _minPoseDetectionConfidence
    val currentMinPoseTrackingConfidence: Float
        get() =
            _minPoseTrackingConfidence
    val currentMinPosePresenceConfidence: Float
        get() =
            _minPosePresenceConfidence

    fun setDelegate(delegate: Int) {
        _delegate = delegate
    }

    fun setMaxPoses(maxPoses: Int) {
        _maxPoses = maxPoses
    }

    fun setMinPoseDetectionConfidence(confidence: Float) {
        _minPoseDetectionConfidence = confidence
    }

    fun setMinPoseTrackingConfidence(confidence: Float) {
        _minPoseTrackingConfidence = confidence
    }

    fun setMinPosePresenceConfidence(confidence: Float) {
        _minPosePresenceConfidence = confidence
    }

    fun setModel(model: Int) {
        _model = model
    }

    fun addPoseReferences(references: List<PoseReference>) {
        val updatedReferences = (_poseReferences.value + references)
            .take(PoseLandmarkerHelper.MAX_NUM_POSE_REFERENCES)
        _poseReferences.value = renumberReferences(updatedReferences)
        persistPoseReferences()
    }

    fun removePoseReference(referenceId: Long) {
        val updatedReferences = _poseReferences.value.filterNot { it.id == referenceId }
        _poseReferences.value = renumberReferences(updatedReferences)
        _passwordReferenceIds.value = _passwordReferenceIds.value.filterNot { it == referenceId }
        persistPoseReferences()
        persistPasswordReferenceIds()
    }

    fun findBestMatchingReference(liveResult: PoseLandmarkerResult): PoseReferenceMatcher.MatchResult {
        return PoseReferenceMatcher.findBestMatch(_poseReferences.value, liveResult)
    }

    fun clearPassword() {
        _passwordReferenceIds.value = emptyList()
        persistPasswordReferenceIds()
    }

    fun appendPasswordReference(referenceId: Long): Boolean {
        if (_passwordReferenceIds.value.size >= MAX_PASSWORD_LENGTH) {
            return false
        }

        _passwordReferenceIds.value = _passwordReferenceIds.value + referenceId
        persistPasswordReferenceIds()
        return true
    }

    fun setAudioPasswordPhrase(phrase: String?) {
        _audioPasswordPhrase.value = phrase?.takeIf { it.isNotBlank() }
        persistAudioPasswordPhrase()
    }

    private fun loadPoseReferences(): List<PoseReference> {
        val json = preferences.getString(POSE_REFERENCES_KEY, null) ?: return emptyList()
        val jsonArray = JSONArray(json)
        val loadedReferences = buildList(jsonArray.length()) {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                val embeddingJson = item.getJSONArray("landmarkEmbedding")
                val embedding = buildList(embeddingJson.length()) {
                    for (embeddingIndex in 0 until embeddingJson.length()) {
                        add(embeddingJson.getDouble(embeddingIndex).toFloat())
                    }
                }

                add(
                    PoseReference(
                        id = item.getLong("id"),
                        name = item.getString("name"),
                        uriString = item.getString("uriString"),
                        detectedPoseCount = item.getInt("detectedPoseCount"),
                        inferenceTimeMs = item.getLong("inferenceTimeMs"),
                        landmarkEmbedding = embedding,
                        isDefault = item.optBoolean("isDefault", false)
                    )
                )
            }
        }
        return renumberReferences(loadedReferences)
    }

    private fun loadPasswordReferenceIds(): List<Long> {
        val json = preferences.getString(PASSWORD_REFERENCE_IDS_KEY, null) ?: return emptyList()
        val jsonArray = JSONArray(json)
        return buildList(jsonArray.length()) {
            for (index in 0 until jsonArray.length()) {
                add(jsonArray.getLong(index))
            }
        }
    }

    private fun loadAudioPasswordPhrase(): String? {
        return preferences.getString(AUDIO_PASSWORD_PHRASE_KEY, DEFAULT_AUDIO_PASSWORD_PHRASE)
    }

    private fun renumberReferences(references: List<PoseReference>): List<PoseReference> {
        var imageIndex = 1
        return references.map { reference ->
            if (reference.isDefault) {
                reference.copy(name = DEFAULT_POSE_REFERENCE_NAME)
            } else {
                reference.copy(name = "Image${imageIndex++}")
            }
        }
    }

    private fun persistPoseReferences() {
        val jsonArray = JSONArray()
        _poseReferences.value.forEach { reference ->
            val jsonObject = JSONObject().apply {
                put("id", reference.id)
                put("name", reference.name)
                put("uriString", reference.uriString)
                put("detectedPoseCount", reference.detectedPoseCount)
                put("inferenceTimeMs", reference.inferenceTimeMs)
                put("landmarkEmbedding", JSONArray(reference.landmarkEmbedding))
                put("isDefault", reference.isDefault)
            }
            jsonArray.put(jsonObject)
        }

        preferences.edit().putString(POSE_REFERENCES_KEY, jsonArray.toString()).apply()
    }

    private fun persistPasswordReferenceIds() {
        preferences.edit()
            .putString(PASSWORD_REFERENCE_IDS_KEY, JSONArray(_passwordReferenceIds.value).toString())
            .apply()
    }

    private fun persistAudioPasswordPhrase() {
        preferences.edit()
            .putString(AUDIO_PASSWORD_PHRASE_KEY, _audioPasswordPhrase.value)
            .apply()
    }

    companion object {
        private const val PREFERENCE_NAME = "pose_reference_prefs"
        private const val POSE_REFERENCES_KEY = "pose_references"
        private const val PASSWORD_REFERENCE_IDS_KEY = "password_reference_ids"
        private const val AUDIO_PASSWORD_PHRASE_KEY = "audio_password_phrase"
        const val DEFAULT_POSE_REFERENCE_NAME = "Default"
        const val DEFAULT_AUDIO_PASSWORD_PHRASE = "hello"
        const val MAX_PASSWORD_LENGTH = 25
    }
}
