package com.google.mediapipe.examples.poselandmarker

data class PoseReference(
    val id: Long,
    val name: String,
    val uriString: String,
    val detectedPoseCount: Int,
    val inferenceTimeMs: Long,
    val landmarkEmbedding: List<Float>
)
