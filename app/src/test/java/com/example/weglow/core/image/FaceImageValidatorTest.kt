package com.example.weglow.core.image

import org.junit.Assert.assertEquals
import org.junit.Test

class FaceImageValidatorTest {
    @Test fun noFaceIsRejected() {
        assertEquals(FaceValidationResult.NoFace, evaluateDetectedFaces(emptyList(), 1000, 1000))
    }

    @Test fun exactlyOneAcceptableFaceIsValid() {
        assertEquals(
            FaceValidationResult.Valid,
            evaluateDetectedFaces(listOf(face()), 1000, 1000),
        )
    }

    @Test fun multipleFacesAreRejected() {
        assertEquals(
            FaceValidationResult.MultipleFaces,
            evaluateDetectedFaces(listOf(face(), face(left = 600, right = 900)), 1000, 1000),
        )
    }

    @Test fun smallFaceIsRejected() {
        assertEquals(
            FaceValidationResult.FaceTooSmall,
            evaluateDetectedFaces(listOf(face(left = 410, top = 410, right = 590, bottom = 590)), 1000, 1000),
        )
    }

    @Test fun heavilyRotatedFaceIsRejected() {
        assertEquals(
            FaceValidationResult.FaceTooRotated,
            evaluateDetectedFaces(listOf(face(yaw = 26f)), 1000, 1000),
        )
    }

    @Test fun faceNearImageBoundaryIsRejected() {
        assertEquals(
            FaceValidationResult.FacePartiallyOutsideImage,
            evaluateDetectedFaces(listOf(face(left = 10)), 1000, 1000),
        )
    }

    @Test fun invalidImageDimensionsReturnProcessingError() {
        assertEquals(
            FaceValidationResult.ProcessingError,
            evaluateDetectedFaces(listOf(face()), 0, 1000),
        )
    }

    private fun face(
        left: Int = 300,
        top: Int = 250,
        right: Int = 700,
        bottom: Int = 750,
        pitch: Float = 0f,
        yaw: Float = 0f,
        roll: Float = 0f,
    ) = DetectedFaceObservation(left, top, right, bottom, pitch, yaw, roll)
}
