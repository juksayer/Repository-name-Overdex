package com.example.overdex.data

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

/**
 * Utility for encoding strings into QR Code bitmaps for trainer identity sharing.
 */
object QrEncoder {
    
    /**
     * Encodes the given text into a square QR Code bitmap.
     * 
     * @param text The string content to encode.
     * @param size The width and height of the resulting square bitmap in pixels.
     * @return A [Bitmap] containing the QR code visual.
     */
    fun encode(text: String, size: Int = 512): Bitmap {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(
            text,
            BarcodeFormat.QR_CODE,
            size,
            size
        )
        
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        
        return bitmap
    }
}
