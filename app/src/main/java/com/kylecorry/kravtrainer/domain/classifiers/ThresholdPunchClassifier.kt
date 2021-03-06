package com.kylecorry.kravtrainer.domain.classifiers

import com.kylecorry.kravtrainer.domain.punches.PunchType
import com.kylecorry.trailsensecore.domain.math.Vector3

class ThresholdPunchClassifier: IPunchClassifier {

    private val antiDuplication = AntiDuplicationFilter(400L)
    private val hookThreshold = 35
    private val straightThreshold = 25

    override fun classify(reading: Vector3): PunchType? {

        if (reading.x > hookThreshold){
            return antiDuplication.filter(PunchType.Hook)
        }

        if (reading.y < -straightThreshold){
            return antiDuplication.filter(PunchType.Straight)
        }

        return antiDuplication.filter(null)
    }

}