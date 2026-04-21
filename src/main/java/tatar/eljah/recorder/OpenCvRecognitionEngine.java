package tatar.eljah.recorder;

import android.graphics.Bitmap;

class OpenCvRecognitionEngine implements ScoreRecognitionEngine {
    private final OpenCvScoreProcessor processor = new OpenCvScoreProcessor();

    @Override
    public OpenCvScoreProcessor.ProcessingResult recognize(Bitmap bitmap,
                                                           String title,
                                                           OpenCvScoreProcessor.ProcessingOptions options) {
        return processor.process(bitmap, title, options);
    }
}
