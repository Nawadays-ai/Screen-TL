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
     * The median colour of the top and bottom bands of a text box, sampled from the capture.
     *
     * Two colours rather than a bitmap: the panel renderer only needs the vertical colour drift of
     * the control, and keeping the capture alive or shipping per-pixel data through OCR would cost
     * far more than the gradient is worth.
     */
    data class PatchSample(val topColor: Int, val bottomColor: Int)

    data class Result(val left:Int,val top:Int,val right:Int,val bottom:Int,val sourceTextSizePx:Float,val backgroundColor:Int,val orientation:WritingOrientation=WritingOrientation.HORIZONTAL,val alignment:TextAlignment=TextAlignment.CENTER,val patchSample:PatchSample?=null)
    fun isVerticalLine(line:Line):Boolean{val box=line.boundingBox?:return false;if(box.width()<=0||box.height()<=0)return false;val ratio=box.height().toFloat()/box.width().toFloat();if(ratio<1.30f)return false;val e=line.elements.mapNotNull{it.boundingBox}.filter{it.width()>0&&it.height()>0};if(e.size<2)return ratio>=1.55f;val v=e.zipWithNext().count{(a,b)->abs(b.centerX()-a.centerX())<=box.width()*0.75f&&b.centerY()>=a.centerY()-box.height()*0.08f};val h=e.zipWithNext().count{(a,b)->abs(b.centerY()-a.centerY())<=box.height()*0.12f&&b.centerX()>=a.centerX()-box.width()*0.08f};return v>=h}
    fun isVerticalBlock(block:TextBlock):Boolean{val l=block.lines;if(l.isEmpty())return false;val v=l.count(::isVerticalLine);return v>0&&(l.size==1||v.toFloat()/l.size>=0.5f)}
    fun verticalBlockText(block:TextBlock):String=block.lines.filter{it.text.isNotBlank()}.sortedByDescending{it.boundingBox?.centerX()?:0}.joinToString(""){it.text.trim()}.trim()
    fun shouldTreatAsParagraph(block:TextBlock):Boolean{if(isVerticalBlock(block))return false;val l=block.lines;if(l.size<2)return false;val b=l.mapNotNull{it.boundingBox}.filter{it.width()>0&&it.height()>0};if(b.size<2)return false;val mh=b.map{it.height()}.sorted()[b.size/2].toFloat().coerceAtLeast(1f);val g=b.sortedBy{it.top}.zipWithNext().map{(a,c)->(c.top-a.bottom).coerceAtLeast(0)};val compact=(g.takeIf{it.isNotEmpty()}?.sorted()?.let{it[it.size/2]}?.toFloat()?:0f)<=mh*0.65f;val lm=b.map{it.left.toFloat()}.average().toFloat();val aligned=b.map{abs(it.left-lm)}.average().toFloat()<=mh*0.85f;val text=block.text.trim();val chars=text.count{!it.isWhitespace()};if(chars<30)return false;val dr=text.count{it.isDigit()}.toFloat()/chars.coerceAtLeast(1);val terminal=l.count{val s=it.text.trimEnd();s.endsWith("。")||s.endsWith("！")||s.endsWith("？")||s.endsWith(".")||s.endsWith("!")||s.endsWith("?")};val p=l.mapNotNull{it.text.trim().takeIf{s->s.length>=4}?.take(6)};val repeated=p.groupingBy{it}.eachCount().values.any{it>=2};val w=b.map{it.width().toFloat()};val wm=w.average().toFloat().coerceAtLeast(1f);var score=0;if(compact)score++;if(aligned)score++;if(chars>=45)score++;if(terminal<=l.size/2)score++;if(dr<0.18f)score++;if(w.map{abs(it-wm)}.average()/wm<0.10f)score--;if(repeated)score-=2;if(dr>=0.28f)score-=2;return score>=3}
    fun analyze(bitmap:Bitmap,block:TextBlock):Result?{val box=block.boundingBox?:return null;if(box.width()<=0||box.height()<=0)return null;val hs=block.lines.flatMap{it.elements.mapNotNull{e->e.boundingBox?.height()?.takeIf{h->h>0}}};val glyph=if(hs.isNotEmpty())hs.sorted()[hs.size/2].toFloat()else box.height().toFloat()/block.lines.size.coerceAtLeast(1);val vertical=isVerticalBlock(block);val orientation=if(vertical)WritingOrientation.VERTICAL else WritingOrientation.HORIZONTAL;val alignment=if(vertical)TextAlignment.CENTER else paragraphAlignment(block.lines.mapNotNull{it.boundingBox}.filter{it.width()>0&&it.height()>0},glyph);return build(bitmap,box.left,box.top,box.right,box.bottom,glyph,orientation,alignment)}
    fun analyze(bitmap:Bitmap,line:Line):Result?{val box=line.boundingBox?:return null;if(box.width()<=0||box.height()<=0)return null;val hs=line.elements.mapNotNull{it.boundingBox?.height()?.takeIf{h->h>0}};val glyph=if(hs.isNotEmpty())hs.sorted()[hs.size/2].toFloat()else box.height().toFloat();return build(bitmap,box.left,box.top,box.right,box.bottom,glyph,if(isVerticalLine(line))WritingOrientation.VERTICAL else WritingOrientation.HORIZONTAL)}
    private fun build(bitmap:Bitmap,l:Int,t:Int,r:Int,b:Int,g:Float,o:WritingOrientation,a:TextAlignment=TextAlignment.CENTER):Result{val hp=(g*.20f).roundToInt().coerceIn(3,18);val vp=(g*.18f).roundToInt().coerceIn(2,12);val left=(l-hp).coerceIn(0,bitmap.width-1);val top=(t-vp).coerceIn(0,bitmap.height-1);val right=(r+hp).coerceIn(left+1,bitmap.width);val bottom=(b+vp).coerceIn(top+1,bitmap.height);val grid=sampleInterior(bitmap,left,top,right,bottom);val background=medianColor(grid.rows.flatMap{it.toList()});return Result(left,top,right,bottom,(g*1.15f).coerceIn(8f,96f),background,o,a,samplePatch(grid,top,bottom))}

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
     * Samples a grid of pixels inside the box the text sits in, one flat array per sampled row.
     *
     * The median of these pixels is the colour of the control behind the text: the text itself is
     * sparse between glyphs at this sampling density, and the median (not the mean) keeps a stray
     * bright glyph from skewing it. Keeping the rows separate — rather than one flat list — is what
     * lets [samplePatch] recover the top and bottom bands without walking the bitmap again.
     */
    private fun sampleInterior(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): SampleGrid {
        val w = right - left; val h = bottom - top
        if (w <= 0 || h <= 0) return SampleGrid(emptyList(), 1)
        val stepX = (w / 16).coerceAtLeast(1)
        val stepY = (h / 12).coerceAtLeast(1)
        val rows = ArrayList<IntArray>(12)
        var y = top
        while (y < bottom) {
            val row = IntArray((w + stepX - 1) / stepX)
            var x = left
            var column = 0
            while (x < right) {
                row[column] = bitmap.getPixel(x.coerceIn(0, bitmap.width - 1), y.coerceIn(0, bitmap.height - 1))
                x += stepX
                column++
            }
            rows.add(row)
            y += stepY
        }
        return SampleGrid(rows, stepY)
    }

    /** The sampled grid plus the row pitch it was taken at, so a band height can be converted to rows. */
    private data class SampleGrid(val rows: List<IntArray>, val stepY: Int)

    /**
     * Reduces the sampled grid to the median colour of the top and bottom bands of the box.
     *
     * The band is the outer 15% at each end, converted from pixels to sampled rows so it needs no
     * second pass over the bitmap. Read as a median because the text itself is sparse there. The
     * renderer mixes these two into a vertical gradient, which is what gives a replacement patch
     * the same lit-top/shadowed-bottom drift as the control it covers. Only the two medians are
     * kept: the source bitmap is not retained and no per-pixel data travels with the result.
     */
    private fun samplePatch(grid: SampleGrid, top: Int, bottom: Int): PatchSample {
        if (grid.rows.isEmpty()) return PatchSample(Color.BLACK, Color.BLACK)
        val bandRows = (((bottom - top).coerceAtLeast(1) * 0.15f) / grid.stepY).roundToInt().coerceIn(1, grid.rows.size)
        val topColor = medianColor(grid.rows.take(bandRows).flatMap { it.toList() })
        val bottomColor = medianColor(grid.rows.takeLast(bandRows).flatMap { it.toList() })
        return PatchSample(topColor, bottomColor)
    }

    private fun medianColor(pixels: List<Int>): Int {
        if (pixels.isEmpty()) return Color.BLACK
        val rs = pixels.map(Color::red).sorted()
        val gs = pixels.map(Color::green).sorted()
        val bs = pixels.map(Color::blue).sorted()
        val m = rs.size / 2
        return Color.rgb(rs[m], gs[m], bs[m])
    }
}
