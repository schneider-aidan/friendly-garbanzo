package com.google.mediapipe.examples.poselandmarker

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.sqrt

object PoseReferenceMatcher {
    private const val LANDMARK_DIMENSIONS = 2
    private const val MATCH_THRESHOLD = 0.22f
    private const val DUPLICATE_THRESHOLD = 0.10f

    data class MatchResult(
        val reference: PoseReference?,
        val distance: Float,
        val isMatch: Boolean
    )

    fun createEmbedding(landmarks: List<NormalizedLandmark>): List<Float> {
        if (landmarks.isEmpty()) {
            return emptyList()
        }

        val centerX = landmarks.map { it.x() }.average().toFloat()
        val centerY = landmarks.map { it.y() }.average().toFloat()

        var maxDistance = 0f
        landmarks.forEach { landmark ->
            val dx = landmark.x() - centerX
            val dy = landmark.y() - centerY
            val distance = sqrt(dx * dx + dy * dy)
            if (distance > maxDistance) {
                maxDistance = distance
            }
        }

        val scale = if (maxDistance > 0f) maxDistance else 1f
        return buildList(landmarks.size * LANDMARK_DIMENSIONS) {
            landmarks.forEach { landmark ->
                add((landmark.x() - centerX) / scale)
                add((landmark.y() - centerY) / scale)
            }
        }
    }

    fun findBestMatch(
        references: List<PoseReference>,
        liveResult: PoseLandmarkerResult
    ): MatchResult {
        if (references.isEmpty()) {
            return MatchResult(null, Float.MAX_VALUE, false)
        }

        var bestReference: PoseReference? = null
        var bestDistance = Float.MAX_VALUE

        liveResult.landmarks().forEach { poseLandmarks ->
            val liveEmbedding = createEmbedding(poseLandmarks)
            if (liveEmbedding.isEmpty()) {
                return@forEach
            }

            references.forEach { reference ->
                val directDistance = embeddingDistance(reference.landmarkEmbedding, liveEmbedding)
                val mirroredDistance = embeddingDistance(
                    reference.landmarkEmbedding,
                    mirrorEmbedding(liveEmbedding)
                )
                val distance = minOf(directDistance, mirroredDistance)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestReference = reference
                }
            }
        }

        return MatchResult(
            reference = bestReference,
            distance = bestDistance,
            isMatch = bestDistance <= MATCH_THRESHOLD
        )
    }

    fun isDuplicatePose(
        existingReferences: List<PoseReference>,
        candidateEmbedding: List<Float>
    ): Boolean {
        if (candidateEmbedding.isEmpty()) {
            return false
        }

        return existingReferences.any { reference ->
            val directDistance = embeddingDistance(reference.landmarkEmbedding, candidateEmbedding)
            val mirroredDistance = embeddingDistance(
                reference.landmarkEmbedding,
                mirrorEmbedding(candidateEmbedding)
            )
            minOf(directDistance, mirroredDistance) <= DUPLICATE_THRESHOLD
        }
    }

    private fun mirrorEmbedding(embedding: List<Float>): List<Float> {
        if (embedding.isEmpty()) {
            return emptyList()
        }

        return buildList(embedding.size) {
            embedding.chunked(LANDMARK_DIMENSIONS).forEach { point ->
                add(-point[0])
                add(point[1])
            }
        }
    }

    private fun embeddingDistance(first: List<Float>, second: List<Float>): Float {
        if (first.size != second.size || first.isEmpty()) {
            return Float.MAX_VALUE
        }

        var total = 0f
        first.indices.forEach { index ->
            val delta = first[index] - second[index]
            total += delta * delta
        }

        return sqrt(total / first.size)
    }
}
