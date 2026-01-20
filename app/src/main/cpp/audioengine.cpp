#include <jni.h>
#include <algorithm>
#include <cmath>
#include <mutex>

struct Biquad {
    float b0=1, b1=0, b2=0, a1=0, a2=0;
    float x1=0, x2=0, y1=0, y2=0;
};

Biquad bands[10];
std::mutex filterMutex; // Prevents crashing if sliders move during processing

// Optimization: We only compute these when the user moves a slider
void computeCoeffs(int i, float freq, float gain) {
    float omega = 2.0f * M_PI * freq / 44100.0f;
    float Q = 1.414f; // Slightly better resonance for 10-band EQ
    float alpha = sin(omega) / (2.0f * Q);
    float cosW = cos(omega);
    float A = pow(10.0f, gain / 40.0f); // Standard peaking EQ conversion

    float b0_raw = 1.0f + alpha * A;
    float b1_raw = -2.0f * cosW;
    float b2_raw = 1.0f - alpha * A;
    float a0_raw = 1.0f + alpha / A;
    float a1_raw = -2.0f * cosW;
    float a2_raw = 1.0f - alpha / A;

    std::lock_guard<std::mutex> lock(filterMutex);
    bands[i].b0 = b0_raw / a0_raw;
    bands[i].b1 = b1_raw / a0_raw;
    bands[i].b2 = b2_raw / a0_raw;
    bands[i].a1 = a1_raw / a0_raw;
    bands[i].a2 = a2_raw / a0_raw;
}

extern "C" JNIEXPORT void JNICALL
Java_com_gongity_iqlizer_AudioService_processBufferNative(
        JNIEnv* env, jobject, jshortArray buffer, jfloat preamp, jfloat finalOut, jfloatArray eqGains) {

jsize len = env->GetArrayLength(buffer);
jshort* samples = env->GetShortArrayElements(buffer, nullptr);
jfloat* gains = env->GetFloatArrayElements(eqGains, nullptr);

float freqs[] = {31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000};

// Update coefficients first (only once per buffer, not per sample!)
for (int b = 0; b < 10; b++) {
computeCoeffs(b, freqs[b], gains[b]);
}

// High-performance sample processing loop
for (int i = 0; i < len; i++) {
float s = static_cast<float>(samples[i]) * preamp;

for (int b = 0; b < 10; b++) {
float out = bands[b].b0 * s + bands[b].b1 * bands[b].x1 + bands[b].b2 * bands[b].x2
            - bands[b].a1 * bands[b].y1 - bands[b].a2 * bands[b].y2;

bands[b].x2 = bands[b].x1;
bands[b].x1 = s;
bands[b].y2 = bands[b].y1;
bands[b].y1 = out;
s = out;
}

// Final output gain and protection against digital clipping
    samples[i] = static_cast<jshort>(std::clamp(s * finalOut, -32768.0f, 32767.0f));
}

// Clean up memory and send the processed audio back to Android
    env->ReleaseShortArrayElements(buffer, samples, 0);
    env->ReleaseFloatArrayElements(eqGains, gains, JNI_ABORT);
} // <--- This closing bracket was missing!