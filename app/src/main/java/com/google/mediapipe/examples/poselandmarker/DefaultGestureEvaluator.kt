package com.google.mediapipe.examples.poselandmarker

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class DefaultGestureEvaluator : GestureEvaluator {

    override fun matches(
        gesture: GestureDefinition,
        landmarks: List<NormalizedLandmark>
    ): Boolean {
        return when (gesture.id) {
            "raised_right_hand" -> matchesRaisedRightHand(landmarks)
            "sumo_squat" -> matchesSumoSquat(landmarks)
            "shrug" -> matchesShrug(landmarks)
            else -> false
        }
    }

    private fun matchesRaisedRightHand(landmarks: List<NormalizedLandmark>): Boolean {
        val rightWrist = landmarks[16]
        val rightShoulder = landmarks[12]
        return rightWrist.y() < rightShoulder.y()
    }

    private fun matchesSumoSquat(landmarks: List<NormalizedLandmark>): Boolean {
        val leftHip = landmarks[23]
        val rightHip = landmarks[24]
        val leftKnee = landmarks[25]
        val rightKnee = landmarks[26]

        val hipsLow = leftHip.y() > leftKnee.y() && rightHip.y() > rightKnee.y()
        return hipsLow
    }

    private fun matchesShrug(landmarks: List<NormalizedLandmark>): Boolean {
        val leftShoulder = landmarks[11]
        val rightShoulder = landmarks[12]
        val nose = landmarks[0]

        val shouldersHigh =
            leftShoulder.y() < nose.y() && rightShoulder.y() < nose.y()

        return shouldersHigh
    }
}