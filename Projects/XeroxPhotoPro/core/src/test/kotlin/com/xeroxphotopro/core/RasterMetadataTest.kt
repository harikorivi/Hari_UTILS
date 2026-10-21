package com.xeroxphotopro.core

import org.junit.Assert.*
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32
import javax.imageio.ImageIO

class RasterMetadataTest {
    private fun encoded(format: String): ByteArray {
        val image = BufferedImage(80, 90, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until 90) for (x in 0 until 80) image.setRGB(x,y, if (x < 40) 0xffffff else 0x006d66)
        return ByteArrayOutputStream().also { ImageIO.write(image, format, it) }.toByteArray()
    }
    @Test fun `PNG stores 300 DPI density CRC and retains all pixels after repeated tagging`() {
        val input = encoded("png")
        val once = ByteArrayOutputStream().also { RasterMetadata.png(input.inputStream(),it) }.toByteArray()
        val result = ByteArrayOutputStream().also { RasterMetadata.png(once.inputStream(),it) }.toByteArray()
        var p = 8; var densityChunks = 0
        while(p < result.size) {
            val length = ByteBuffer.wrap(result,p,4).int
            val type = String(result,p+4,4,Charsets.US_ASCII)
            if(type == "pHYs") {
                densityChunks++
                assertEquals(11811,ByteBuffer.wrap(result,p+8,4).int)
                assertEquals(11811,ByteBuffer.wrap(result,p+12,4).int)
                assertEquals(1,result[p+16].toInt())
                val crc = CRC32().apply { update(result,p+4,length+4) }
                assertEquals(crc.value.toInt(),ByteBuffer.wrap(result,p+8+length,4).int)
            }
            p += length + 12
        }
        assertEquals(1,densityChunks)
        val before = ImageIO.read(input.inputStream()); val after = ImageIO.read(result.inputStream())
        assertEquals(80, after.width); assertEquals(90,after.height)
        for (y in 0 until 90) for (x in 0 until 80) assertEquals(before.getRGB(x,y),after.getRGB(x,y))
    }
    @Test fun `JPEG has one JFIF at 300 DPI and decodes unchanged`() {
        val input=encoded("jpg")
        val once=ByteArrayOutputStream().also { RasterMetadata.jpeg(input.inputStream(),it) }.toByteArray()
        val result=ByteArrayOutputStream().also { RasterMetadata.jpeg(once.inputStream(),it) }.toByteArray()
        assertEquals("JFIF",String(result,6,4,Charsets.US_ASCII))
        assertEquals(1,result[13].toInt())
        assertEquals(300,ByteBuffer.wrap(result,14,2).short.toInt()); assertEquals(300,ByteBuffer.wrap(result,16,2).short.toInt())
        val before=ImageIO.read(input.inputStream()); val after=ImageIO.read(result.inputStream())
        assertEquals(before.getRGB(60,40),after.getRGB(60,40)); assertEquals(80,after.width)
        assertArrayEquals(once,result)
    }
}
