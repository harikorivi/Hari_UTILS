package com.xeroxphotopro.core

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import kotlin.math.roundToInt

/** Adds density to freshly encoded images without decoding or keeping a sheet-sized byte array. */
object RasterMetadata {
    fun png(input: InputStream, output: OutputStream, dpi: Int = 300) {
        val source = DataInputStream(input)
        val target = DataOutputStream(output)
        val signature = ByteArray(8).also(source::readFully)
        require(signature.contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)))
        target.write(signature)
        while (true) {
            val length = source.readInt()
            require(length in 0..16_777_216) { "Invalid PNG chunk." }
            val type = ByteArray(4).also(source::readFully)
            val data = ByteArray(length).also(source::readFully)
            val crc = source.readInt()
            val name = type.toString(Charsets.US_ASCII)
            if (name != "pHYs") { target.writeInt(length); target.write(type); target.write(data); target.writeInt(crc) }
            if (name == "IHDR") {
                val density = (dpi / .0254).roundToInt()
                val payload = java.io.ByteArrayOutputStream().also { buffer ->
                    DataOutputStream(buffer).apply { writeInt(density); writeInt(density); writeByte(1) }
                }.toByteArray()
                val tag = "pHYs".toByteArray(Charsets.US_ASCII)
                val check = CRC32().apply { update(tag); update(payload) }
                target.writeInt(payload.size); target.write(tag); target.write(payload); target.writeInt(check.value.toInt())
            }
            if (name == "IEND") break
        }
        target.flush()
    }
    fun jpeg(input: InputStream, output: OutputStream, dpi: Int = 300) {
        require(dpi in 1..65535)
        val source = DataInputStream(input)
        val target = DataOutputStream(output)
        require(source.readUnsignedShort() == 0xffd8)
        target.writeShort(0xffd8)
        target.writeShort(0xffe0); target.writeShort(16)
        target.write(byteArrayOf(74, 70, 73, 70, 0, 1, 2, 1)) // JFIF 1.02, dots per inch
        target.writeShort(dpi); target.writeShort(dpi); target.writeShort(0)
        while (true) {
            require(source.readUnsignedByte() == 255) { "Invalid JPEG marker." }
            var marker = source.readUnsignedByte()
            while (marker == 255) marker = source.readUnsignedByte()
            if (marker == 0xd9) { target.writeShort(0xffd9); break }
            val length = source.readUnsignedShort()
            require(length >= 2)
            val data = ByteArray(length - 2).also(source::readFully)
            val isJfif = marker == 0xe0 && data.size >= 5 && data.copyOfRange(0, 5).contentEquals(byteArrayOf(74, 70, 73, 70, 0))
            if (!isJfif) { target.writeByte(255); target.writeByte(marker); target.writeShort(length); target.write(data) }
            if (marker == 0xda) { source.copyTo(target); break }
        }
        target.flush()
    }
}
