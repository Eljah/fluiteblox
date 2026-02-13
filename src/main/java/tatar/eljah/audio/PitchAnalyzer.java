package tatar.eljah.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

public class PitchAnalyzer {

    public interface PitchListener {
        void onPitch(float pitchHz);
    }

    public interface SpectrumListener {
        void onSpectrum(float[] magnitudes, int sampleRate);
    }

    public interface AudioListener {
        void onAudio(short[] samples, int length, int sampleRate);
    }

    private volatile boolean running;
    private Thread workerThread;

    public void startRealtimePitch(final PitchListener listener) {
        startRealtimePitch(listener, null);
    }

    public void startRealtimePitch(final PitchListener listener, final SpectrumListener spectrumListener) {
        startRealtimePitch(listener, spectrumListener, null);
    }

    public void startRealtimePitch(final PitchListener listener,
                                   final SpectrumListener spectrumListener,
                                   final AudioListener audioListener) {
        if (running) {
            return;
        }
        running = true;

        workerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                int sampleRate = 22050;
                int channelConfig = AudioFormat.CHANNEL_IN_MONO;
                int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

                int minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                if (minBufferSize <= 0) {
                    running = false;
                    return;
                }

                AudioRecord record = new AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        minBufferSize
                );

                short[] buffer = new short[minBufferSize];

                try {
                    if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                        running = false;
                        return;
                    }
                    record.startRecording();
                    while (running) {
                        int read = record.read(buffer, 0, buffer.length);
                        if (read > 0) {
                            float pitch = estimatePitch(buffer, read, sampleRate);
                            if (pitch > 0f && listener != null) {
                                listener.onPitch(pitch);
                            }
                            if (spectrumListener != null) {
                                float[] magnitudes = computeSpectrum(buffer, read);
                                spectrumListener.onSpectrum(magnitudes, sampleRate);
                            }
                            if (audioListener != null) {
                                short[] samples = new short[read];
                                System.arraycopy(buffer, 0, samples, 0, read);
                                audioListener.onAudio(samples, read, sampleRate);
                            }
                        }
                    }
                    record.stop();
                } catch (IllegalStateException ignored) {
                    running = false;
                } finally {
                    record.release();
                }
            }
        });
        workerThread.start();
    }

    public void analyzePcm(short[] samples, int sampleRate, PitchListener listener, SpectrumListener spectrumListener) {
        if (samples == null || samples.length == 0) {
            return;
        }
        int frameSize = 1024;
        int hopSize = 512;
        if (samples.length < frameSize) {
            frameSize = samples.length;
            hopSize = Math.max(1, frameSize / 2);
        }
        short[] frame = new short[frameSize];
        for (int start = 0; start + frameSize <= samples.length; start += hopSize) {
            System.arraycopy(samples, start, frame, 0, frameSize);
            float pitch = estimatePitch(frame, frameSize, sampleRate);
            if (pitch > 0f && listener != null) {
                listener.onPitch(pitch);
            }
            if (spectrumListener != null) {
                float[] magnitudes = computeSpectrum(frame, frameSize);
                spectrumListener.onSpectrum(magnitudes, sampleRate);
            }
        }
    }

    public void stop() {
        running = false;
        if (workerThread != null) {
            try {
                workerThread.join();
            } catch (InterruptedException ignored) {
            }
            workerThread = null;
        }
    }

    private float estimatePitch(short[] buffer, int length, int sampleRate) {
        int crossings = 0;
        for (int i = 1; i < length; i++) {
            short prev = buffer[i - 1];
            short curr = buffer[i];
            if ((prev >= 0 && curr < 0) || (prev < 0 && curr >= 0)) {
                crossings++;
            }
        }
        if (crossings == 0) {
            return 0f;
        }
        float frequency = (sampleRate * crossings) / (2f * length);
        if (frequency < 120f || frequency > 2600f) {
            return 0f;
        }
        return frequency;
    }

    private float[] computeSpectrum(short[] buffer, int length) {
        int size = 1;
        while (size * 2 <= length && size < 2048) {
            size *= 2;
        }
        if (size < 128) {
            size = Math.min(length, 128);
        }
        double[] real = new double[size];
        double[] imag = new double[size];
        for (int i = 0; i < size; i++) {
            double window = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * i / (size - 1));
            real[i] = buffer[i] * window;
            imag[i] = 0.0;
        }
        fft(real, imag);
        int bins = size / 2;
        float[] magnitudes = new float[bins];
        for (int i = 0; i < bins; i++) {
            double mag = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
            magnitudes[i] = (float) mag;
        }
        return magnitudes;
    }

    private void fft(double[] real, double[] imag) {
        int n = real.length;
        int bits = (int) (Math.log(n) / Math.log(2));
        for (int i = 0; i < n; i++) {
            int j = reverseBits(i, bits);
            if (j > i) {
                double tmpReal = real[i];
                double tmpImag = imag[i];
                real[i] = real[j];
                imag[i] = imag[j];
                real[j] = tmpReal;
                imag[j] = tmpImag;
            }
        }
        for (int size = 2; size <= n; size <<= 1) {
            int halfSize = size / 2;
            double phaseStep = -2.0 * Math.PI / size;
            for (int i = 0; i < n; i += size) {
                for (int j = 0; j < halfSize; j++) {
                    double angle = j * phaseStep;
                    double cos = Math.cos(angle);
                    double sin = Math.sin(angle);
                    int evenIndex = i + j;
                    int oddIndex = evenIndex + halfSize;
                    double tre = cos * real[oddIndex] - sin * imag[oddIndex];
                    double tim = sin * real[oddIndex] + cos * imag[oddIndex];
                    real[oddIndex] = real[evenIndex] - tre;
                    imag[oddIndex] = imag[evenIndex] - tim;
                    real[evenIndex] += tre;
                    imag[evenIndex] += tim;
                }
            }
        }
    }

    private int reverseBits(int value, int bits) {
        int reversed = 0;
        for (int i = 0; i < bits; i++) {
            reversed = (reversed << 1) | (value & 1);
            value >>= 1;
        }
        return reversed;
    }
}
