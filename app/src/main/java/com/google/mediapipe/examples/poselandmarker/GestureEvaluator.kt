package com.google.mediapipe.examples.poselandmarker

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

interface GestureEvaluator {
    fun matches(gesture: GestureDefinition, landmarks: List<NormalizedLandmark>): Boolean
}