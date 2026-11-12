package com.example.overdex.battle.observation

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import com.example.overdex.battle.custody.TerminalAppearanceMeasurement
import com.example.overdex.battle.custody.TerminalAppearanceSupport
import com.example.overdex.model.AnchorRegion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Bounded appearance measurement. No species, match result, damage or faint is inferred. */
internal class TerminalAppearanceFrameMeasurer {
    private data class Seed(val at: Long, val bins: List<Int>, val area: Int, val largest: Int,
        val indices: IntArray, val values: IntArray)
    private var raster: Bitmap? = null
    private var canvas: Canvas? = null
    private val paint by lazy { Paint(Paint.FILTER_BITMAP_FLAG) }
    private var pixels = IntArray(0)
    private var codes = IntArray(0)
    private var values = IntArray(0)
    private var labels = IntArray(0)
    private var queue = IntArray(0)
    private var previousBackground = IntArray(0)
    private var geometry: List<Int>? = null
    private var lastAt: Long? = null
    private var lastSeedAt = Long.MIN_VALUE
    private val seeds = ArrayList<Seed>()

    fun reset() { seeds.clear(); geometry = null; lastAt = null; lastSeedAt = Long.MIN_VALUE }
    fun close() { reset(); raster?.recycle(); raster = null; canvas = null }

    fun measure(bitmap: Bitmap, regions: List<AnchorRegion>, at: Long): TerminalAppearanceMeasurement {
        val w = minOf(336, bitmap.width)
        val h = (bitmap.height.toDouble() * w / bitmap.width).roundToInt()
        val bounds = regions.map { region -> BattleCropResolver.resolveRect(region, w, h)
            ?.let { BattleCropBounds(it.left,it.top,it.right,it.bottom) } }
        if (lastAt?.let { at > it && at-it<50_000_000L } == true)
            return TerminalAppearanceMeasurement("THROTTLED",w,h,bounds.filterNotNull().map(::coordinates))
        if (w < 32 || h !in 32..1344 || bounds.any { it == null }) {
            reset(); return TerminalAppearanceMeasurement("INVALID",w,h,emptyList())
        }
        if (raster?.width != w || raster?.height != h) {
            raster?.recycle(); raster = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            canvas = Canvas(requireNotNull(raster))
            pixels = IntArray(w*h)
        }
        return try {
            canvas!!.drawBitmap(bitmap,null,Rect(0,0,w,h),paint)
            raster!!.getPixels(pixels,0,w,0,0,w,h)
            measure(w,h,bounds.filterNotNull(),at,pixels)
        } catch (_: RuntimeException) {
            reset(); TerminalAppearanceMeasurement("UNREADABLE",w,h,bounds.filterNotNull().map(::coordinates))
        }
    }

    /** Host/device tests supply the same measured raster without Android bitmap calls. */
    internal fun measure(w: Int, h: Int, regions: List<BattleCropBounds>, at: Long,
        image: IntArray): TerminalAppearanceMeasurement {
        val coords = regions.map(::coordinates)
        fun unavailable(status: String): TerminalAppearanceMeasurement {
            reset(); return TerminalAppearanceMeasurement(status,w,h,coords)
        }
        if (w !in 32..336 || h !in 32..1344 || image.size != w*h || regions.size != 3 || at < 0 ||
            regions.any { it.left < 0 || it.top < 0 || it.right > w || it.bottom > h ||
                it.right-it.left < 8 || it.bottom-it.top < 8 }) return unavailable("INVALID")
        if (lastAt?.let { at <= it } == true) return TerminalAppearanceMeasurement("OLD_FRAME",w,h,coords)
        if (image.any { it ushr 24 < 230 }) return unavailable("UNREADABLE")
        val nextGeometry = listOf(w,h)+coords.flatten()
        val changed = geometry != null && geometry != nextGeometry
        val oldAt = lastAt
        val contiguous = !changed && oldAt != null && at-oldAt in 1..250_000_000L
        if (!contiguous) { seeds.clear(); lastSeedAt = Long.MIN_VALUE }
        if (codes.size != w*h) {
            codes=IntArray(w*h); values=IntArray(w*h); labels=IntArray(w*h); queue=IntArray(w*h)
        }
        val near=regions[0]; val far=regions[1]; val background=regions[2]
        val bgSize=area(background)
        if (previousBackground.size != bgSize) previousBackground=IntArray(bgSize)
        var sum=0.0; var squares=0.0; var difference=0L; var bi=0
        for (y in background.top until background.bottom) for (x in background.left until background.right) {
            val c=image[y*w+x]; val old=previousBackground[bi]
            val r=c ushr 16 and 255; val g=c ushr 8 and 255; val b=c and 255
            val luminance=.299*r+.587*g+.114*b
            sum+=luminance; squares+=luminance*luminance
            difference+=abs(r-(old ushr 16 and 255))+abs(g-(old ushr 8 and 255))+abs(b-(old and 255))
            previousBackground[bi++]=c
        }
        val mean=sum/bgSize; val texture=sqrt((squares/bgSize-mean*mean).coerceAtLeast(0.0))
        val delta=if(contiguous) difference.toDouble()/(bgSize*3) else null
        geometry=nextGeometry; lastAt=at
        val status=when { changed -> "GEOMETRY_CHANGED"; oldAt==null -> "BASELINE"
            !contiguous -> "SOURCE_GAP"; delta!!>5 || texture<8 || mean !in 20.0..230.0 -> "BACKGROUND_CHANGED"
            else -> "SAMPLED" }
        if(status!="SAMPLED") {
            seeds.clear(); lastSeedAt=Long.MIN_VALUE
            return TerminalAppearanceMeasurement(status,w,h,coords,delta,texture,mean,
                previousSourceAtNanos=oldAt.takeIf { contiguous })
        }
        val nearHist=IntArray(18); val farHist=IntArray(18)
        fun histogram(bounds: BattleCropBounds, histogram: IntArray) {
            for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
                val i=y*w+x; val c=image[i]
                val r=c ushr 16 and 255; val g=c ushr 8 and 255; val b=c and 255
                val high=maxOf(r,g,b); val low=minOf(r,g,b); val diff=high-low
                values[i]=high
                val saturation=if(high==0) 0 else (255.0*diff/high).roundToInt()
                val code=if(saturation<90 || high<80 || diff==0) -1 else {
                    val hue=when(high) { r -> (g-b).toDouble()/diff; g -> 2.0+(b-r).toDouble()/diff
                        else -> 4.0+(r-g).toDouble()/diff }
                    ((hue*30).roundToInt()+180)%180/10
                }
                codes[i]=code
                if(code>=0) histogram[code]++
            }
        }
        histogram(near,nearHist); histogram(far,farHist)
        seeds.removeAll { at-it.at>2_000_000_000L }
        val supports=seeds.map { seed ->
            var bright=0
            for(i in seed.indices.indices) if(values[seed.indices[i]]-seed.values[i]>40) bright++
            TerminalAppearanceSupport(seed.at,seed.bins,seed.area,seed.largest,
                seed.bins.sumOf { nearHist[it] },bright.toDouble()/seed.area)
        }.toMutableList()
        if(lastSeedAt==Long.MIN_VALUE || at-lastSeedAt>=100_000_000L) {
            val bins=(0..17).filter { nearHist[it].toDouble()/area(near)>.03 &&
                nearHist[it].toDouble()/area(near)>3*(farHist[it].toDouble()/area(far)+.005) }
            val total=bins.sumOf { nearHist[it] }
            // Scale support by measured-raster area; factory 336x747 uses 3000 pixels.
            if(total>=w.toDouble()*h*(3000.0/(336*747))) {
                val chosen=BooleanArray(18); bins.forEach { chosen[it]=true }
                val indices=IntArray(total); var size=0
                for(y in near.top until near.bottom) for(x in near.left until near.right) {
                    val i=y*w+x; val matched=codes[i]>=0 && chosen[codes[i]]
                    labels[i]=if(matched) -1 else 0
                    if(matched) indices[size++]=i
                }
                var largest=0
                for(start in indices) {
                    if(labels[start]!=-1) continue
                    var head=0; var tail=1; queue[0]=start; labels[start]=1
                    while(head<tail) {
                        val i=queue[head++]; val x=i%w; val y=i/w
                        for(dy in -1..1) for(dx in -1..1) {
                            val xx=x+dx; val yy=y+dy
                            if(xx !in near.left until near.right || yy !in near.top until near.bottom) continue
                            val next=yy*w+xx
                            if(labels[next]==-1) { labels[next]=1; queue[tail++]=next }
                        }
                    }
                    largest=maxOf(largest,tail)
                }
                if(largest>=.7*total) {
                    if(seeds.size>=21 || seeds.sumOf { it.area }+total>1_000_000) return unavailable("CLUTTER")
                    seeds+=Seed(at,bins,total,largest,indices,IntArray(total) { values[indices[it]] })
                    supports+=TerminalAppearanceSupport(at,bins,total,largest,total,0.0)
                    lastSeedAt=at
                }
            }
        }
        return TerminalAppearanceMeasurement(status,w,h,coords,delta,texture,mean,supports,
            previousSourceAtNanos=oldAt)
    }

    companion object {
        private fun area(b: BattleCropBounds)=(b.right-b.left)*(b.bottom-b.top)
        private fun coordinates(b: BattleCropBounds)=listOf(b.left,b.top,b.right,b.bottom)
    }
}
