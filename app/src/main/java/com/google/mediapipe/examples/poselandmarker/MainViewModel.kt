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

    val currentDelegate: Int get() = _delegate
    val currentModel: Int get() = _model
    val currentMaxPoses: Int get() = _maxPoses
    val poseReferences: StateFlow<List<PoseReference>> = _poseReferences.asStateFlow()
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
        persistPoseReferences()
    }

    fun findBestMatchingReference(liveResult: PoseLandmarkerResult): PoseReferenceMatcher.MatchResult {
        return PoseReferenceMatcher.findBestMatch(_poseReferences.value, liveResult)
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
                        landmarkEmbedding = embedding
                    )
                )
            }
        }
        return renumberReferences(loadedReferences)
    }

    private fun renumberReferences(references: List<PoseReference>): List<PoseReference> {
        return references.mapIndexed { index, reference ->
            reference.copy(name = "Image${index + 1}")
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
            }
            jsonArray.put(jsonObject)
        }

        preferences.edit().putString(POSE_REFERENCES_KEY, jsonArray.toString()).apply()
    }

    companion object {
        private const val PREFERENCE_NAME = "pose_reference_prefs"
        private const val POSE_REFERENCES_KEY = "pose_references"
    }
}
