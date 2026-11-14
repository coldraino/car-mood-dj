package com.carmooddj.app

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * 데모 음원을 앱 안에서 직접 합성해 재생합니다.
 * 음원 파일을 쓰지 않으므로 저작권 문제가 없습니다.
 */
class SynthPlayer {

    private class Pattern(
        val bpm: Int,
        val notes: IntArray,   // 8분음표 단위 멜로디 (MIDI 번호, 0 = 쉼표)
        val bass: IntArray,    // 4분음표 단위 베이스 (0 = 쉼표)
        val wave: Int,         // 0 사인, 1 따뜻한 음색, 2 벨, 3 밝은 음색
        val decay: Double,     // 샘플당 감쇠 (1에 가까울수록 길게 울림)
        val kick: Boolean
    )

    private val sampleRate = 44100

    @Volatile private var mood: Mood = Mood.NORMAL
    @Volatile private var playing = true
    @Volatile private var variation = 0
    @Volatile private var running = false
    private var thread: Thread? = null

    fun setMood(value: Mood) { mood = value }
    fun setPlaying(value: Boolean) { playing = value }
    fun setVariation(value: Int) { variation = value }

    fun start() {
        if (running) return
        running = true
        thread = Thread({ loop() }, "CarDJ-Synth").also { it.start() }
    }

    fun stop() {
        running = false
        try {
            thread?.join(500)
        } catch (_: InterruptedException) {
        }
        thread = null
    }

    private fun patternFor(m: Mood): Pattern = when (m) {
        Mood.NORMAL -> Pattern(
            100,
            intArrayOf(60, 64, 67, 72, 67, 64, 60, 64, 65, 69, 72, 77, 72, 69, 65, 69),
            intArrayOf(48, 48, 48, 48, 53, 53, 53, 53),
            1, 0.99975, false
        )
        Mood.NIGHT -> Pattern(
            76,
            intArrayOf(57, 0, 64, 0, 67, 0, 72, 0, 65, 0, 69, 0, 72, 0, 64, 0),
            intArrayOf(45, 45, 45, 45, 41, 41, 41, 41),
            0, 0.99988, false
        )
        Mood.RAIN -> Pattern(
            68,
            intArrayOf(62, 0, 69, 65, 0, 74, 0, 69, 60, 0, 67, 64, 0, 72, 0, 67),
            intArrayOf(38, 38, 38, 38, 36, 36, 36, 36),
            0, 0.99990, false
        )
        Mood.SNOW -> Pattern(
            84,
            intArrayOf(84, 0, 79, 81, 0, 76, 79, 0, 86, 0, 81, 84, 0, 79, 76, 0),
            intArrayOf(48, 0, 48, 0, 53, 0, 55, 0),
            2, 0.99982, false
        )
        Mood.ARRIVING -> Pattern(
            132,
            intArrayOf(72, 76, 79, 76, 72, 76, 79, 84, 74, 77, 81, 77, 74, 77, 81, 86),
            intArrayOf(48, 48, 55, 55, 50, 50, 57, 57),
            3, 0.99955, true
        )
    }

    private fun midiToHz(note: Int): Double = 440.0 * 2.0.pow((note - 69) / 12.0)

    private fun voice(wave: Int, phase: Double): Double = when (wave) {
        1 -> sin(phase) + 0.3 * sin(2 * phase)
        2 -> sin(phase) + 0.5 * sin(3 * phase) + 0.2 * sin(5 * phase)
        3 -> sin(phase) + 0.5 * sin(2 * phase) + 0.3 * sin(3 * phase)
        else -> sin(phase)
    }

    private fun loop() {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuffer, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (e: Exception) {
            running = false
            return
        }

        val twoPi = 2 * PI
        val buffer = ShortArray(1024)
        var currentMood = mood
        var pattern = patternFor(currentMood)
        var transpose = 0
        var step = 0
        var posInStep = 0
        var stepSamples = sampleRate * 60 / pattern.bpm / 2

        var leadPhase = 0.0
        var leadFreq = 0.0
        var leadEnv = 0.0
        var bassPhase = 0.0
        var bassFreq = 0.0
        var kickPhase = 0.0
        var kickEnv = 0.0
        var master = 0.0

        try {
            track.play()
            while (running) {
                for (i in buffer.indices) {
                    if (posInStep == 0) {
                        if (currentMood != mood) {
                            currentMood = mood
                            pattern = patternFor(currentMood)
                            stepSamples = sampleRate * 60 / pattern.bpm / 2
                            step = 0
                        }
                        transpose = (variation % 4) * 2
                        val note = pattern.notes[step % pattern.notes.size]
                        if (note > 0) {
                            leadFreq = midiToHz(note + transpose)
                            leadEnv = 1.0
                        }
                        val bassNote = pattern.bass[(step / 2) % pattern.bass.size]
                        bassFreq = if (bassNote > 0) midiToHz(bassNote + transpose) else 0.0
                        if (pattern.kick && step % 2 == 0) {
                            kickEnv = 1.0
                            kickPhase = 0.0
                        }
                    }

                    val target = if (playing) 1.0 else 0.0
                    master += (target - master) * 0.0008

                    val lead = voice(pattern.wave, leadPhase) * leadEnv
                    leadEnv *= pattern.decay
                    val bass = if (bassFreq > 0.0) sin(bassPhase) * 0.5 else 0.0
                    val kick = sin(kickPhase) * kickEnv
                    kickEnv *= 0.9990

                    val sample = (lead * 0.30 + bass * 0.45 + kick * 0.5) * master * 0.6
                    val clipped = if (sample > 1.0) 1.0 else if (sample < -1.0) -1.0 else sample
                    buffer[i] = (clipped * 32767.0).toInt().toShort()

                    leadPhase += twoPi * leadFreq / sampleRate
                    if (leadPhase > twoPi) leadPhase -= twoPi
                    bassPhase += twoPi * bassFreq / sampleRate
                    if (bassPhase > twoPi) bassPhase -= twoPi
                    kickPhase += twoPi * (45.0 + 110.0 * kickEnv) / sampleRate

                    posInStep++
                    if (posInStep >= stepSamples) {
                        posInStep = 0
                        step++
                    }
                }
                track.write(buffer, 0, buffer.size)
            }
        } catch (_: Exception) {
        } finally {
            try {
                track.stop()
            } catch (_: Exception) {
            }
            track.release()
        }
    }
}
