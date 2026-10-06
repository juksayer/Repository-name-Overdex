package com.example.overdex.battle.audio

/** Signed little-endian PCM16 peak, including the asymmetric -32768 endpoint. */
object PcmSignalLevel {
    fun peak(bytes: ByteArray, count: Int = bytes.size): Float {
        var peak = 0
        for (index in 0 until (count.coerceAtMost(bytes.size) / 2) * 2 step 2) {
            val sample = ((bytes[index].toInt() and 255) or (bytes[index + 1].toInt() shl 8)).toShort().toInt()
            peak = maxOf(peak, kotlin.math.abs(sample))
        }
        return peak / 32768f
    }
}
