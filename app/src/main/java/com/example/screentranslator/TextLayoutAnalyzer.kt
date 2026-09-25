package com.example.screentranslator

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.text.Text.Line
import com.google.mlkit.vision.text.Text.TextBlock
import kotlin.math.abs
import kotlin.math.roundToInt

/** Converts ML Kit paragraph/line geometry into rendering geometry for the Manual overlay. */
object TextLayoutAnalyzer {
    enum class WritingOrientation { HORIZONTAL, VERTICAL }
    enum class TextAlignment { LEFT, CENTER, RIGHT }

    /**
     * Colours of the text the OCR box covers, sampled from the crop itself.
     *
     * [fill] is the median colour of the pixel cluster furthest from the box background — the
     * glyphs — and [stroke] the cluster sitting between fill and background, which is the outline
     * when [hasStroke] is true. [separation] is the luminance distance between fill and
     * background: below ~60 the sampled fill is not trustworthy and the renderer falls back to
     * white/black with a contrasting outline.
     */
    data class GlyphStyle(val fill: Int, val stroke: Int, val hasStroke: Boolean, val separation: Float)

    data class Result(val left:Int,val top:Int,val right:Int,val bottom:Int,val sourceTextSizePx:Float,val backgroundColor:Int,val orientation:WritingOrientation=WritingOrientation.HORIZONTAL,val glyphStyle:GlyphStyle?=null,val alignment:TextAlignment=TextAlignment.CENTER)
    fun isVerticalLine(line:Line):Boolean{val box=line.boundingBox?:return false;if(box.width()<=0||box.height()<=0)return false;val ratio=box.height().toFloat()/box.width().toFloat();if(ratio<1.30f)return false;val e=line.elements.mapNotNull{it.boundingBox}.filter{it.width()>0&&it.height()>0};if(e.size<2)return ratio>=1.55f;val v=e.zipWithNext().count{(a,b)->abs(b.centerX()-a.centerX())<=box.width()*0.75f&&b.centerY()>=a.centerY()-box.height()*0.08f};val h=e.zipWithNext().count{(a,b)->abs(b.centerY()-a.centerY())<=box.height()*0.12f&&b.centerX()>=a.centerX()-box.width()*0.08f};return v>=h}
    fun isVerticalBlock(block:TextBlock):Boolean{val l=block.lines;if(l.isEmpty())return false;val v=l.count(::isVerticalLine);return v>0&&(l.size==1||v.toFloat()/l.size>=0.5f)}
    fun verticalBlockText(block:TextBlock):String=block.lines.filter{it.text.isNotBlank()}.sortedByDescending{it.boundingBox?.centerX()?:0}.joinToString(""){it.text.trim()}.trim()
    fun shouldTreatAsParagraph(block:TextBlock):Boolean{if(isVerticalBlock(block))return false;val l=block.lines;if(l.size<2)return false;val b=l.mapNotNull{it.boundingBox}.filter{it.width()>0&&it.height()>0};if(b.size<2)return false;val mh=b.sorted()[b.size/2].toFloat().coerceAtLeast(1f);val g=b.sortedBy{it.top}.zipWithNext().map{(a,c)->(c.top-a.bottom).coerceAtLeast(0)};val compact=(g.takeIf{it.isNotEmpty()}?.sorted()?.let{it[it.size/2]}?.toFloat()?:0f)<=mh*0.65f;val lm=b.map{it.left.toFloat()}.average().toFloat();val aligned=b.map{abs(it.left-lm)}.average().toFloat()<=mh*0.85f;val text=block.text.trim();val chars=text.count{!it.isWhitespace()};if(chars<30)return false;val dr=text.count{it.isDigit()}.toFloat()/chars.coerceAtLeast(1);val terminal=l.count{val s=it.text.trimEnd();s.endsWith("。")||s.endsWith("！")||s.endsWith("？")||s.endsWith(".")||s.endsWith("!")||s.endsWith("?")};val p=l.mapNotNull{it.text.trim().takeIf{s->s.length>=4}?.take(6)};val repeated=p.groupingBy{it}.eachCount().values.any{it>=2};val w=b.map{it.width().toFloat()};val wm=w.average().coerceAtLeast(1f);var score=0;if(compact)score++;if(aligned)score++;if(chars>=45)score++;if(terminal<=l.size/2)score++;if(dr<0.18f)score++;if(w.map{abs(it-wm)}.average()/wm<0.10f)score--;if(repeated)score-=2;if(dr>=0.28f)score-=2;return score>=3}
    fun analyze(bitmap:Bitmap,block:TextBlock):Result?{val box=block.boundingBox?:return null;if(box.width()<=0||box.height()<=0)return null;val hs=block.lines.flatMap{it.elements.mapNotNull{e->e.boundingBox?.height()?.takeIf{h->h>0}}};val glyph=if(hs.isNotEmpty())hs.sorted()[hs.size/2].toFloat()else box.height().toFloat()/block.lines.size.coerceAtLeast(1);val vertical=isVerticalBlock(block);val orientation=if(vertical)WritingOrientation.VERTICAL else WritingOrientation.HORIZONTAL;val alignment=if(vertical)TextAlignment.CENTER else paragraphAlignment(block.lines.mapNotNull{it.boundingBox}.filter{it.width()>0&&it.height()>0},glyph);return build(bitmap,box.left,box.top,box.right,box.bottom,glyph,orientation,alignment)}
    fun analyze(bitmap:Bitmap,line:Line):Result?{val box=line.boundingBox?:return null;if(box.width()<=0||box.height()<=0)return null;val hs=line.elements.mapNotNull{it.boundingBox?.height()?.takeIf{h->h>0}};val glyph=if(hs.isNotEmpty())hs.sorted()[hs.size/2].toFloat()else box.height().toFloat();return build(bitmap,box.left,box.top,box.right,box.bottom,glyph,if(isVerticalLine(line))WritingOrientation.VERTICAL else WritingOrientation.HORIZONTAL)}
    private fun build(bitmap:Bitmap,l:Int,t:Int,r:Int,b:Int,g:Float,o:WritingOrientation,a:TextAlignment=TextAlignment.CENTER):Result{val hp=(g*.20f).roundToInt().coerceIn(3,18);val vp=(g*.18f).roundToInt().coerceIn(2,12);val left=(l-hp).coerceIn(0,bitmap.width-1);val top=(t-vp).coerceIn(0,bitmap.height-1);val right=(r+hp).coerceIn(left+1,bitmap.width);val bottom=(b+vp).coerceIn(top+1,bitmap.height);val pixels=sampleInterior(bitmap,left,top,right,bottom);val background=medianColor(pixels);return Result(left,top,right,bottom,(g*1.15f).coerceIn(8f,96f),background,o,sampleGlyphStyle(pixels,background),a)}

    /**
     * Infers how the original paragraph was aligned from its line boxes.
     *
     * Only meaningful for multi-line blocks: a single line hugs its own box, so alignment cannot
     * be told apart from the geometry and CENTER (the previous behaviour) stays the default.
     */
    private fun paragraphAlignment(boxes: List<Rect>, glyph: Float): TextAlignment {
        if (boxes.size < 2) return TextAlignment.CENTER
        val leftSpread = boxes.maxOf { it.left } - boxes.minOf { it.left }
        if (leftSpread <= glyph * 0.5f) return TextAlignment.LEFT
        val rightSpread = boxes.maxOf { it.right } - boxes.minOf { it.right }
        if (rightSpread <= glyph * 0.5f) return TextAlignment.RIGHT
        return TextAlignment.CENTER
    }

    /**
     * Samples a grid of pixels inside the box the text sits in.
     *
     * The median of these pixels is the colour of the control behind the text: the text itself is
     * sparse between glyphs at this sampling density, and the median (not the mean) keeps a stray
     * bright glyph from skewing it. The same pixel list is then split into luminance clusters to
     * recover the glyph colours — see [sampleGlyphStyle].
     */
    private fun sampleInterior(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): List<Int> {
        val w = right - left; val h = bottom - top
        if (w <= 0 || h <= 0) return emptyList()
        val stepX = (w / 16).coerceAtLeast(1)
        val stepY = (h / 12).coerceAtLeast(1)
        val pixels = ArrayList<Int>(224)
        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                pixels.add(bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1)))
                x += stepX
            }
            y += stepY
        }
        return pixels
    }

    private fun medianColor(pixels: List<Int>): Int {
        if (pixels.isEmpty()) return Color.BLACK
        val rs = pixels.map(Color::red).sorted()
        val gs = pixels.map(Color::green).sorted()
        val bs = pixels.map(Color::blue).sorted()
        val m = rs.size / 2
        return Color.rgb(rs[m], gs[m], bs[m])
    }

    private fun luminance(color: Int): Float =
        0.2126f * Color.red(color) + 0.7152f * Color.green(color) + 0.0722f * Color.blue(color)

    /**
     * Splits the sampled interior into up to three luminance clusters and reads the text style
     * off them.
     *
     * Game text is usually three colours: background, outline, and fill. The cluster furthest
     * from the background median is taken as the fill; the cluster between fill and background is
     * taken as the outline only when it stands far enough from the background to be a real ring —
     * otherwise it is just more background and no stroke should be drawn. Clusters holding under
     * 4% of the samples are merged into their nearest neighbour first, so single stray pixels
     * cannot become a "colour". A null result means the box is effectively one colour and the
     * renderer should fall back to white/black.
     */
    private fun sampleGlyphStyle(pixels: List<Int>, background: Int): GlyphStyle? {
        if (pixels.size < 16) return null
        val bgLum = luminance(background)
        val lums = FloatArray(pixels.size) { luminance(pixels[it]) }
        val sorted = lums.sorted()
        var c0 = sorted[(sorted.size * 0.05f).toInt()]
        var c1 = sorted[(sorted.size * 0.50f).toInt()]
        var c2 = sorted[(sorted.size * 0.95f).toInt()]
        if (c2 - c0 < 8f) return null
        val assignment = IntArray(pixels.size)
        fun assign() {
            for (i in lums.indices) {
                val d0 = abs(lums[i] - c0); val d1 = abs(lums[i] - c1); val d2 = abs(lums[i] - c2)
                assignment[i] = when { d0 <= d1 && d0 <= d2 -> 0; d1 <= d2 -> 1; else -> 2 }
            }
        }
        for (iteration in 0 until 8) {
            assign()
            val sums = FloatArray(3); val counts = IntArray(3)
            for (i in lums.indices) { sums[assignment[i]] += lums[i]; counts[assignment[i]]++ }
            val n0 = if (counts[0] > 0) sums[0] / counts[0] else c0
            val n1 = if (counts[1] > 0) sums[1] / counts[1] else c1
            val n2 = if (counts[2] > 0) sums[2] / counts[2] else c2
            val delta = abs(n0 - c0) + abs(n1 - c1) + abs(n2 - c2)
            c0 = n0; c1 = n1; c2 = n2
            if (delta < 0.5f) break
        }
        assign()
        val centroids = floatArrayOf(c0, c1, c2)
        val counts = IntArray(3)
        for (a in assignment) counts[a]++
        val minCount = (pixels.size * 0.04f).toInt().coerceAtLeast(3)
        for (k in 0..2) {
            if (counts[k] == 0 || counts[k] >= minCount) continue
            val target = (0..2).filter { it != k }.minByOrNull { abs(centroids[it] - centroids[k]) } ?: continue
            for (i in assignment.indices) if (assignment[i] == k) assignment[i] = target
            counts[target] += counts[k]; counts[k] = 0
        }
        val medLum = FloatArray(3)
        val medColor = IntArray(3)
        val valid = BooleanArray(3)
        for (k in 0..2) {
            if (counts[k] == 0) continue
            val members = pixels.indices.filter { assignment[it] == k }
            if (members.isEmpty()) continue
            valid[k] = true
            val memberLums = members.map { lums[it] }.sorted()
            medLum[k] = memberLums[memberLums.size / 2]
            medColor[k] = medianColor(members.map { pixels[it] })
        }
        if (valid.count { it } < 2) return null
        val fillK = (0..2).filter { valid[it] }.maxByOrNull { abs(medLum[it] - bgLum) } ?: return null
        val fillLum = medLum[fillK]
        val separation = abs(fillLum - bgLum)
        if (separation < 1f) return null
        val strokeK = (0..2).filter { valid[it] && it != fillK }.maxByOrNull { abs(medLum[it] - bgLum) } ?: return null
        val strokeLum = medLum[strokeK]
        // A ring only counts as an outline when it visibly departs from the background; the
        // distance to the fill keeps a near-fill cluster from being stroked. The bar against the
        // background is deliberately low: a dark outline on a dark panel differs little in
        // luminance, and a wrong guess is harmless because a stroke the colour of the panel is
        // invisible anyway.
        val hasStroke = abs(strokeLum - bgLum) >= 15f && abs(strokeLum - fillLum) >= 40f
        return GlyphStyle(fill = medColor[fillK], stroke = medColor[strokeK], hasStroke = hasStroke, separation = separation)
    }
}
