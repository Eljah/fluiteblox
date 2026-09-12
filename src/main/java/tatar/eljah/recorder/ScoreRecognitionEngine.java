package tatar.eljah.recorder;

import android.graphics.Bitmap;

interface ScoreRecognitionEngine {
    OpenCvScoreProcessor.ProcessingResult recognize(Bitmap bitmap,
                                                    String title,
                                                    OpenCvScoreProcessor.ProcessingOptions options);
}
