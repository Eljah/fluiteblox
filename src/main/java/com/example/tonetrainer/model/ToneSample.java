package com.example.tonetrainer.model;

import java.util.List;

public class ToneSample {
    private final List<Float> pitchHz;
    private final int frameStepMs;

    public ToneSample(List<Float> pitchHz, int frameStepMs) {
        this.pitchHz = pitchHz;
        this.frameStepMs = frameStepMs;
    }

    public List<Float> getPitchHz() {
        return pitchHz;
    }

    public int getFrameStepMs() {
        return frameStepMs;
    }

    public Direction getDirection() {
        return getDirection(20f, 2);
    }

    public Direction getDirection(float thresholdHz, int minValidSamples) {
        if (pitchHz == null || pitchHz.size() < minValidSamples) {
            return Direction.FLAT;
        }
        float first = 0f;
        float last = 0f;
        boolean firstSet = false;
        int validSamples = 0;
        for (Float value : pitchHz) {
            if (value != null && value > 0f) {
                if (!firstSet) {
                    first = value;
                    firstSet = true;
                }
                last = value;
                validSamples++;
            }
        }
        if (!firstSet || validSamples < minValidSamples) {
            return Direction.FLAT;
        }
        float diff = last - first;
        if (diff > thresholdHz) {
            return Direction.RISING;
        } else if (diff < -thresholdHz) {
            return Direction.FALLING;
        } else {
            return Direction.FLAT;
        }
    }

    public enum Direction {
        RISING,
        FALLING,
        FLAT
    }
}
