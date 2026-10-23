package com.example.overdex.battle.observation

import kotlin.math.*

/** Experimental geometric witness. A separator is not an energy count or a move use. */
internal object ChargeButtonFillHeightReader {
    data class Reading(val centerX: Int, val centerY: Int, val radius: Int,
        val separatorY: Float?, val status: String) {
        val fraction: Float? get() = separatorY?.let { (centerY + radius - it) / (2f * radius) }
    }
    private val directions = (0 until 24).map { val a = it * PI / 12; cos(a) to sin(a) }
    fun measure(width: Int, height: Int, pixels: IntArray, slot: Int): Reading? {
        require(slot in 0..1)
        require(pixels.size == width * height)
        val minRadius = max(8, (width * .09).toInt())
        val maxRadius = min((width * .16).toInt(), height / 2 - 2)
        if (maxRadius < minRadius) return null
        fun white(x: Int, y: Int): Boolean {
            if (x !in 0 until width || y !in 0 until height) return false
            val c = pixels[y * width + x]
            val r = c shr 16 and 255; val g = c shr 8 and 255; val b = c and 255
            return minOf(r,g,b) > 160 && maxOf(r,g,b) - minOf(r,g,b) < 65
        }
        fun score(cx: Int, cy: Int, r: Int): Int = directions.count { (dx,dy) ->
            (-1..1).any { d -> white((cx+dx*(r+d)).roundToInt(), (cy+dy*(r+d)).roundToInt()) }
        }
        var best: Triple<Int,Int,Int>? = null
        var bestScore = 0
        val step = max(3, minRadius / 10)
        val startX = (width * (slot * .5 + .15)).toInt()
        val endX = (width * (slot * .5 + .38)).toInt()
        for (r in minRadius..maxRadius step step) {
            for (y in r+2 until height-(r*.65).toInt()-1 step step) for (x in startX..endX step step) {
                val s = score(x,y,r)
                if (s > bestScore) { bestScore=s; best=Triple(x,y,r) }
            }
        }
        val seed = best ?: return null
        val tied = mutableListOf<Triple<Int,Int,Int>>()
        for (r in max(minRadius,seed.third-step)..min(maxRadius,seed.third+step)) {
            for (y in seed.second-step..seed.second+step) for (x in seed.first-step..seed.first+step) {
                if (y-r < 0 || y+(r*.65).toInt() >= height || x-r < 0 || x+r >= width) continue
                val s = score(x,y,r)
                if (s > bestScore) { bestScore=s; tied.clear() }
                if (s == bestScore) tied.add(Triple(x,y,r))
            }
        }
        if (tied.isNotEmpty()) best = Triple(tied.map { it.first }.average().roundToInt(),
            tied.map { it.second }.average().roundToInt(), tied.map { it.third }.average().roundToInt())
        if (bestScore < 20) return null // Reject missing, clipped, faded, and badly distorted rims.
        val (cx,cy,rim) = best!!
        val radius = rim // The bright outline surrounds the fill; retain its measured radius.
        val boundary = separator(width,height,pixels,cx,cy,radius)
        return Reading(cx,cy,radius,boundary,
            if (boundary == null) "UNKNOWN_SEPARATOR" else "EXPERIMENTAL_SEPARATOR")
    }

    fun separator(width: Int, height: Int, pixels: IntArray, cx: Int, cy: Int, radius: Int): Float? {
        val inner = (radius * .8667f).toInt()
        if (radius < 8 || cx-inner < 0 || cy-inner < 0 || cx+inner >= width || cy+inner >= height) return null
        val rows = mutableListOf<Int>()
        for (dy in -inner..inner) {
            val extent = sqrt((inner*inner-dy*dy).toDouble()).toInt()
            val start = max((radius/3f).roundToInt(),extent/2)
            if (extent-start+1 < max(3,(radius*.1067).roundToInt())) continue
            val both = listOf(-1,1).all { side ->
                var hits=0
                for (dx in start..extent) {
                    val c=pixels[(cy+dy)*width+cx+side*dx]
                    val r=c shr 16 and 255; val g=c shr 8 and 255; val b=c and 255
                    if (minOf(r,g,b)>110 && maxOf(r,g,b)-minOf(r,g,b)<140) hits++
                }
                hits.toFloat()/(extent-start+1)>.7f
            }
            if (both) rows.add(dy)
        }
        if (rows.isEmpty() || rows.size > max(2,(radius*8f/75).roundToInt()) ||
            rows.last()-rows.first()+1 != rows.size) return null
        return cy+(rows.first()+rows.last())/2f
    }

    /** Every layer remains possible; pixel tolerance is not a statistical confidence interval. */
    fun energyRanges(fraction: Float?, cost: Int, fractionTolerance: Float): List<ClosedFloatingPointRange<Float>> {
        if (fraction == null || !fraction.isFinite() || fraction !in 0f..1f || cost !in 1..100 ||
            !fractionTolerance.isFinite() || fractionTolerance < 0f) return emptyList()
        return (0..100/cost).mapNotNull { layer ->
            val value=(layer+fraction)*cost
            val low=max(0f,value-fractionTolerance*cost); val high=min(100f,value+fractionTolerance*cost)
            if (low<=high) low..high else null
        }
    }
}
