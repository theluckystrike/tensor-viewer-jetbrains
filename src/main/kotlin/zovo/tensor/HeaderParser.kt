package zovo.tensor

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads only the header of a GGUF, safetensors, .npy or PyTorch file, for the plain view shown when JCEF is missing.
 * A 70 GB GGUF is read up to the end of its tensor list (a few MB), never further.
 */
object HeaderParser {
    private const val MAX_GGUF_HEADER = 256L * 1024 * 1024
    private const val MAX_SAFETENSORS_HEADER = 100L * 1024 * 1024
    private const val MAX_LIST = 400

    fun describe(file: File): String = try {
        val head = readHead(file, 16)
        when {
            head.size >= 4 && String(head, 0, 4, Charsets.US_ASCII) == "GGUF" -> gguf(file)
            head.size >= 6 && head[0] == 0x93.toByte() && String(head, 1, 5, Charsets.US_ASCII) == "NUMPY" -> npy(readHead(file, 1 shl 20))
            head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() ->
                if (file.name.lowercase().endsWith(".npz")) "NPZ archive (zip). The array list is shown in the full viewer, which needs JCEF."
                else "PyTorch checkpoint (zip). Tensor names and shapes are shown in the full viewer, which needs JCEF."
            head.size >= 2 && (head[0].toInt() and 0xff) == 0x80 -> "PyTorch checkpoint (pickle, protocol ${head[1]}). Tensor names and shapes are shown in the full viewer, which needs JCEF."
            file.name.lowercase().endsWith(".safetensors") -> safetensors(file)
            else -> "Unknown format (no GGUF, safetensors, NumPy or PyTorch signature in the first bytes)."
        }
    } catch (e: Exception) {
        "Could not read the header: " + (e.message ?: e.javaClass.simpleName)
    }

    private fun readHead(file: File, max: Int): ByteArray = RandomAccessFile(file, "r").use { raf ->
        val n = minOf(raf.length(), max.toLong()).toInt()
        ByteArray(n).also { raf.readFully(it) }
    }

    // ---------- GGUF ----------

    /** Little-endian reader with a hard cap on the bytes it may consume. */
    private class LeReader(input: InputStream, private val cap: Long) {
        private val din = DataInputStream(BufferedInputStream(input, 1 shl 16))
        var pos = 0L; private set
        private fun need(n: Long) { if (pos + n > cap) throw IllegalStateException("the header is larger than ${cap / 1048576} MB"); pos += n }
        fun u8(): Int { need(1); return din.readUnsignedByte() }
        fun u16(): Int { need(2); val b = din.readUnsignedShort(); return ((b and 0xff) shl 8) or (b ushr 8) }
        fun u32(): Long { need(4); return Integer.reverseBytes(din.readInt()).toLong() and 0xffffffffL }
        fun i32(): Int { need(4); return Integer.reverseBytes(din.readInt()) }
        fun u64(): Long { need(8); return java.lang.Long.reverseBytes(din.readLong()) }
        fun f32(): Float = java.lang.Float.intBitsToFloat(i32())
        fun f64(): Double { need(8); return java.lang.Double.longBitsToDouble(java.lang.Long.reverseBytes(din.readLong())) }
        fun skip(n: Long) { need(n); var left = n; while (left > 0) { val k = din.skip(left); if (k <= 0) { din.readByte(); left-- } else left -= k } }
        fun string(keep: Int = 200): String {
            val n = u64()
            if (n < 0 || n > cap) throw IllegalStateException("bad string length $n")
            val take = minOf(n, keep.toLong()).toInt()
            need(take.toLong()); val b = ByteArray(take); din.readFully(b)
            if (n > take) skip(n - take)
            val s = String(b, Charsets.UTF_8)
            return if (n > take) "$s... (${"%,d".format(n)} bytes)" else s
        }
    }

    private val GGML_TYPES = mapOf(0 to "F32", 1 to "F16", 2 to "Q4_0", 3 to "Q4_1", 6 to "Q5_0", 7 to "Q5_1", 8 to "Q8_0", 9 to "Q8_1",
        10 to "Q2_K", 11 to "Q3_K", 12 to "Q4_K", 13 to "Q5_K", 14 to "Q6_K", 15 to "Q8_K", 16 to "IQ2_XXS", 17 to "IQ2_XS", 18 to "IQ3_XXS",
        19 to "IQ1_S", 20 to "IQ4_NL", 21 to "IQ3_S", 22 to "IQ2_S", 23 to "IQ4_XS", 24 to "I8", 25 to "I16", 26 to "I32", 27 to "I64",
        28 to "F64", 29 to "IQ1_M", 30 to "BF16", 34 to "TQ1_0", 35 to "TQ2_0", 39 to "MXFP4")

    private fun ggufValue(r: LeReader, type: Int, version: Long): String = when (type) {
        0 -> r.u8().toString(); 1 -> r.u8().toByte().toString(); 2 -> r.u16().toString(); 3 -> r.u16().toShort().toString()
        4 -> r.u32().toString(); 5 -> r.i32().toString(); 6 -> r.f32().toString(); 7 -> (r.u8() != 0).toString()
        8 -> r.string()
        9 -> {
            val itemType = r.u32().toInt()
            val n = if (version == 1L) r.u32() else r.u64()
            val shown = ArrayList<String>()
            for (i in 0 until n) { val v = ggufValue(r, itemType, version); if (i < 8) shown.add(if (itemType == 8) "\"$v\"" else v) }
            "[${"%,d".format(n)} items] " + shown.joinToString(", ") + if (n > 8) ", ..." else ""
        }
        10 -> java.lang.Long.toUnsignedString(r.u64()); 11 -> r.u64().toString(); 12 -> r.f64().toString()
        else -> throw IllegalStateException("unknown GGUF value type $type")
    }

    fun gguf(file: File): String = FileInputStream(file).use { input ->
        val r = LeReader(input, minOf(file.length(), MAX_GGUF_HEADER))
        r.skip(4)
        val version = r.u32()
        if (version !in 1L..3L) return "GGUF version $version is not known to this fallback view."
        val tensorCount = if (version == 1L) r.u32() else r.u64()
        val kvCount = if (version == 1L) r.u32() else r.u64()
        val out = StringBuilder("GGUF version $version\ntensors: ${"%,d".format(tensorCount)}\nmetadata keys: ${"%,d".format(kvCount)}\n\nMetadata\n")
        for (i in 0 until kvCount) {
            val key = r.string(500)
            val value = ggufValue(r, r.u32().toInt(), version)
            out.append("  ").append(key).append(" = ").append(if (key == "tokenizer.chat_template") "(${value.length} characters shown in the full viewer)" else value.take(300)).append('\n')
        }
        out.append("\nTensors\n")
        var params = 0L
        for (i in 0 until tensorCount) {
            val name = r.string(300)
            val nd = r.u32().toInt()
            val dims = LongArray(nd) { if (version == 1L) r.u32() else r.u64() }
            val type = r.u32().toInt()
            r.u64() // offset
            params += dims.fold(1L) { a, b -> a * b }
            if (i < MAX_LIST) out.append("  ").append(name).append("  ").append(GGML_TYPES[type] ?: "type $type").append("  [").append(dims.reversed().joinToString(", ")).append("]\n")
        }
        if (tensorCount > MAX_LIST) out.append("  ... ${"%,d".format(tensorCount - MAX_LIST)} more\n")
        out.append("\nparameters: ${"%,d".format(params)}\nheader bytes read: ${"%,d".format(r.pos)} of ${"%,d".format(file.length())}")
        out.toString()
    }

    // ---------- safetensors ----------

    fun safetensors(file: File): String {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 8) return "Not a safetensors file: it is shorter than 8 bytes."
            val lenBytes = ByteArray(8); raf.readFully(lenBytes)
            val n = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).long
            if (n <= 0 || n > MAX_SAFETENSORS_HEADER || 8 + n > raf.length()) return "Not a valid safetensors file: the header length ($n) does not fit the file."
            val json = ByteArray(n.toInt()); raf.readFully(json)
            return safetensorsHeader(String(json, Charsets.UTF_8), raf.length())
        }
    }

    private val ENTRY = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\\{\\s*\"(dtype|shape|data_offsets)\"")
    private val DTYPE = Regex("\"dtype\"\\s*:\\s*\"([A-Za-z0-9_]+)\"")
    private val SHAPE = Regex("\"shape\"\\s*:\\s*\\[([^\\]]*)]")

    /** Lists tensors (name, dtype, shape) and the __metadata__ keys. Plain regex over the flat header JSON. */
    fun safetensorsHeader(json: String, fileSize: Long): String {
        val tensors = ArrayList<Triple<String, String, List<Long>>>()
        for (m in ENTRY.findAll(json)) {
            val name = m.groupValues[1]
            if (name == "__metadata__") continue
            val open = json.indexOf('{', m.groups[1]!!.range.last)
            val end = json.indexOf('}', open)
            val body = if (open >= 0 && end > open) json.substring(open, end + 1) else ""
            val dtype = DTYPE.find(body)?.groupValues?.get(1) ?: "?"
            val shape = SHAPE.find(body)?.groupValues?.get(1)?.split(',')?.mapNotNull { it.trim().toLongOrNull() } ?: emptyList()
            tensors.add(Triple(name, dtype, shape))
        }
        val meta = Regex("\"__metadata__\"\\s*:\\s*\\{([^}]*)}").find(json)?.groupValues?.get(1)
        val metaPairs = if (meta == null) emptyList() else Regex("\"((?:[^\"\\\\]|\\\\.)*)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").findAll(meta).map { it.groupValues[1] to it.groupValues[2] }.toList()
        val params = tensors.sumOf { t -> t.third.fold(1L) { a, b -> a * b } }
        val dtypes = tensors.groupingBy { it.second }.eachCount().entries.sortedByDescending { it.value }.joinToString(", ") { "${it.key} x${it.value}" }
        val out = StringBuilder("safetensors\ntensors: ${"%,d".format(tensors.size)}\nparameters: ${"%,d".format(params)}\ndtypes: $dtypes\nheader: ${"%,d".format(json.length)} bytes of ${"%,d".format(fileSize)}\n")
        if (metaPairs.isNotEmpty()) {
            out.append("\n__metadata__\n")
            for ((k, v) in metaPairs) out.append("  ").append(k).append(" = ").append(v.take(300)).append('\n')
        }
        out.append("\nTensors\n")
        for (t in tensors.sortedBy { it.first }.take(MAX_LIST)) out.append("  ").append(t.first).append("  ").append(t.second).append("  [").append(t.third.joinToString(", ")).append("]\n")
        if (tensors.size > MAX_LIST) out.append("  ... ${"%,d".format(tensors.size - MAX_LIST)} more\n")
        return out.toString().trimEnd()
    }

    // ---------- NumPy ----------

    fun npy(b: ByteArray): String {
        val major = b[6].toInt()
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        val (len, start) = if (major == 1) (bb.getShort(8).toInt() and 0xffff) to 10 else bb.getInt(8) to 12
        require(start + len <= b.size) { "header longer than the file" }
        val dict = String(b, start, len, Charsets.ISO_8859_1).trim()
        val descr = Regex("'descr'\\s*:\\s*'([^']*)'").find(dict)?.groupValues?.get(1) ?: "(structured)"
        val fortran = Regex("'fortran_order'\\s*:\\s*(True|False)").find(dict)?.groupValues?.get(1) ?: "?"
        val shape = Regex("'shape'\\s*:\\s*\\(([^)]*)\\)").find(dict)?.groupValues?.get(1)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        return "NumPy .npy version $major\ndtype: $descr\nshape: (${shape.joinToString(", ")}${if (shape.size == 1) "," else ""})\nfortran_order: $fortran"
    }

}
