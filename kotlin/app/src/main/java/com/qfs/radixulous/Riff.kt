package com.qfs.radixulous

class SoundFont {
    // Mandatory INFO
    var ifil: Pair<Int, Int> = Pair(0,0)
    var isng: String = "EMU8000"
    var inam: String = ""

    //Optional INFO
    var irom: String? = null
    var iver: Pair<Int, Int>? = null
    var icrd: String? = null // Date
    var ieng: String? = null
    var iprd: String? = null
    var icop: String? = null
    var icmt: String? = null
    var isft: String? = null

    // Populated by sdta
    // NOTE: smpl size needs to be 2 * sm24 size
    var sampleData: ByteArray = ByteArray(0)

    var presets: List<Preset> = listOf()

    constructor(riff: Riff) {
        var tmp_sample_a: ByteArray? = null
        var tmp_sample_b: ByteArray? = null
        var pdta_index: Int = 0
        riff.list_chunks.forEachIndexed { i, list_chunk ->
            when (list_chunk.type) {
                "INFO" -> {
                    for (sub_chunk in list_chunk.sub_chunks) {
                        var bytes = sub_chunk.bytes
                        when (sub_chunk.type) {
                            "ifil" -> {
                                this.ifil = Pair(
                                    bytes[0].toInt() + (bytes[1].toInt() * 256),
                                    bytes[2].toInt() + (bytes[3].toInt() * 256)
                                )
                            }
                            "isng" -> {
                                this.isng = bytes.toString()
                            }
                            "INAM" -> {
                                this.inam = bytes.toString()
                            }
                            "irom" -> {
                                this.irom = bytes.toString()
                            }
                            "iver" -> {
                                this.iver = Pair(
                                    bytes[0].toInt() + (bytes[1].toInt() * 256),
                                    bytes[2].toInt() + (bytes[3].toInt() * 256)
                                )
                            }
                            "ICRD" -> {
                                this.icrd = bytes.toString()
                            }
                            "IENG" -> {
                                this.ieng = bytes.toString()
                            }
                            "IPRD" -> {
                                this.iprd = bytes.toString()
                            }
                            "ICOP" -> {
                                this.icop = bytes.toString()
                            }
                            "ICMT" -> {
                                this.icmt = bytes.toString()
                            }
                            "ISFT" -> {
                                this.isft = bytes.toString()
                            }
                            else -> {} // Throw error
                        }
                    }
                }
                "sdta" -> {
                    for (sub_chunk in list_chunk.sub_chunks) {
                        var bytes = sub_chunk.bytes
                        when (sub_chunk.type) {
                            "smpl" -> {
                                tmp_sample_a = bytes
                            }
                            "sm24" -> {
                                tmp_sample_b = bytes
                            }
                            else -> {} // Throw error
                        }
                    }
                }
                "pdta" -> {
                    pdta_index = i
                }
                else -> {}
            }
        }

        // Merge sample data and convert to big-endian
        if (tmp_sample_a != null) {
            if (tmp_sample_b != null) {
                this.sampleData = ByteArray(tmp_sample_a!!.size + tmp_sample_b!!.size)
                for (i in 0 until tmp_sample_a!!.size) {
                    this.sampleData[(i * 3)] = tmp_sample_a!![(2 * i) + 1]
                    this.sampleData[(i * 3) + 1] = tmp_sample_a!![(2 * i)]
                }
                for (i in 0 until tmp_sample_b!!.size) {
                    this.sampleData[(i * 3) + 2] = tmp_sample_b!![i]
                }
            } else {
                this.sampleData = ByteArray(tmp_sample_a!!.size)
                for (i in 0 until tmp_sample_a!!.size) {
                    this.sampleData[(i * 2)] = tmp_sample_a!![(2 * i) + 1]
                    this.sampleData[(i * 2) + 1] = tmp_sample_a!![(2 * i)]
                }
            }
        }

        var pdta_chunk = riff.list_chunks[pdta_index]

        // Make a hashmap for easier access
        var pdta_map = HashMap<String, SubChunk>()
        for (sub_chunk in pdta_chunk.sub_chunks) {
            pdta_map[sub_chunk.type] = sub_chunk
        }

        var pgenerators: MutableList<Triple<Int, Int, Int>> = mutableListOf()
        for (i in 0 until pdta_map["pgen"]!!.bytes.size / 4) {
            pgenerators.add(
                Triple(
                    pdta_map["pgen"]!!.bytes[(i * 4)].toInt() + (pdta_map["pgen"]!!.bytes[(i * 4) + 1].toInt() * 256),
                    pdta_map["pgen"]!!.bytes[(i * 4) + 2].toInt(),
                    pdta_map["pgen"]!!.bytes[(i * 4) + 3].toInt()
                )
            )
        }
        var pmodulators: MutableList<Modulator> = mutableListOf()
        var pmod_bytes = pdta_map["pmod"]!!.bytes
        for (i in 0 until pmod_bytes.size / 10) {
            pmodulators.add(
                Modulator(
                    pmod_bytes[(i * 10)].toInt() + (pmod_bytes[(i * 10) + 1].toInt() * 256),
                    pmod_bytes[(i * 10) + 2].toInt() + (pmod_bytes[(i * 10) + 3].toInt() * 256),
                    pmod_bytes[(i * 10) + 4].toInt() + (pmod_bytes[(i * 10) + 5].toInt() * 256),
                    pmod_bytes[(i * 10) + 6].toInt() + (pmod_bytes[(i * 10) + 7].toInt() * 256),
                    pmod_bytes[(i * 10) + 8].toInt() + (pmod_bytes[(i * 10) + 9].toInt() * 256)
                )
            )
        }
        var igenerators: MutableList<Triple<Int, Int, Int>> = mutableListOf()
        for (i in 0 until pdta_map["igen"]!!.bytes.size / 4) {
            igenerators.add(
                Triple(
                    pdta_map["igen"]!!.bytes[(i * 4)].toInt() + (pdta_map["igen"]!!.bytes[(i * 4) + 1].toInt() * 256),
                    pdta_map["igen"]!!.bytes[(i * 4) + 2].toInt(),
                    pdta_map["igen"]!!.bytes[(i * 4) + 3].toInt()
                )
            )
        }
        var imodulators: MutableList<Modulator> = mutableListOf()
        var imod_bytes = pdta_map["imod"]!!.bytes
        for (i in 0 until imod_bytes.size / 10) {
            imodulators.add(
                Modulator(
                    imod_bytes[(i * 10)].toInt() + (imod_bytes[(i * 10) + 1].toInt() * 256),
                    imod_bytes[(i * 10) + 2].toInt() + (imod_bytes[(i * 10) + 3].toInt() * 256),
                    imod_bytes[(i * 10) + 4].toInt() + (imod_bytes[(i * 10) + 5].toInt() * 256),
                    imod_bytes[(i * 10) + 6].toInt() + (imod_bytes[(i * 10) + 7].toInt() * 256),
                    imod_bytes[(i * 10) + 8].toInt() + (imod_bytes[(i * 10) + 9].toInt() * 256)
                )
            )
        }


        var preset_count = pdta_map["phdr"]!!.bytes.size / 38
        var pbag_entry_size = 4
        var ibag_entry_size = 4


        for (i in 0 until preset_count) {
            var wPresetBagIndex = pdta_map["phdr"]!!.bytes[(i * 38) + 24] + (pdta_map["phdr"]!!.bytes[(i * 38) + 25] * 256)

            var wGenNdx = pdta_map["pbag"]!!.bytes[(wPresetBagIndex * pbag_entry_size)] + (pdta_map["pbag"]!!.bytes[(wPresetBagIndex * pbag_entry_size) + 1] * 256)

            var wModNdx = pdta_map["pbag"]!!.bytes[(wPresetBagIndex * pbag_entry_size) + 2] + (pdta_map["pbag"]!!.bytes[(wPresetBagIndex * pbag_entry_size) + 3] * 256)

            var sfModList = (0 until 10)
                .map { j: Int -> pdta_map["pmod"]!!.bytes[j + (wModNdx * 10)] }
                .toByteArray()

            //TODO  Come Back to PMOD
            var preset_generators: MutableList<Triple<Int, Int, Int>> = mutableListOf()
            var j = 0
            // TODO: change this to be the difference between presets' wGenNdx's
            while (wGenNdx + j < pgenerators.size) {
                var generator = pgenerators[wGenNdx + j]
                preset_generators.add(generator)
                if (generator.first == 41) {
                    break
                }
                j += 1
            }

            var wInstGenNdx = pdta_map["ibag"]!!.bytes[(i * ibag_entry_size)] + (pdta_map["ibag"]!!.bytes[(i * ibag_entry_size) + 1] * 256)

            var wInstModNdx = pdta_map["ibag"]!!.bytes[(i * ibag_entry_size) + 2] + (pdta_map["ibag"]!!.bytes[(i * ibag_entry_size) + 3] * 256)

            var instrument_generators: MutableList<Triple<Int, Int, Int>> = mutableListOf()
            j = 0
            while (wInstGenNdx + j < igenerators.size) {
                var generator = igenerators[wInstGenNdx + j]
                instrument_generators.add(generator)
                if (generator.first == 53) {
                    break
                }
                j += 1
            }



            var preset = Preset(
                // TODO: May need to drop 0's
                ((i * 38) until ((i * 38) + 20))
                    .map { j: Int -> pdta_map["phdr"]!!.bytes[j + (i * 38)] }
                    .toString(),
                pdta_map["phdr"]!!.bytes[(i * 38) + 20] + (pdta_map["phdr"]!!.bytes[(i * 38) + 21] * 256),
                pdta_map["phdr"]!!.bytes[(i * 38) + 22] + (pdta_map["phdr"]!!.bytes[(i * 38) + 22] * 256),
                preset_generators,
                pmodulators[wModNdx],
                instrument_generators,
                imodulators[wInstModNdx]
            )
        }
    }
}

data class Modulator(
    var sfModSrcOper: Int,
    var sfModDestOper: Int,
    var modAmount: Int,
    var sfModAmtSrcOper: Int,
    var sfModTransOper: Int
)

data class SFSample(
    var name: String,
    var chunk: ByteArray,
    var loopStart: Int,
    var loopEnd: Int,
    var sampleRate: Int,
    var originalPitch: Int,
    var pithCorrection: Int
)

enum class SFModulator {}
enum class SFGenerator {}
enum class Transform {}

class Preset(
    var name: String = "",
    var preset: Int = 0, // MIDI Preset Number
    var bank: Int = 0, // MIDI Bank Number
    // dwLibrary, dwGenre, dwMorphology don't do anything yet
    var preset_generators: List<Triple<Int, Int, Int>>,
    var preset_modulator: Modulator,
    var instrument_generators: List<Triple<Int, Int, Int>>,
    var instrument_modulator: Modulator
)


open class RiffChunk(var type: String)
class Riff(type: String, var list_chunks: List<ListChunk>): RiffChunk(type)
class ListChunk(type: String, var sub_chunks: List<SubChunk>): RiffChunk(type)
data class SubChunk(var type: String, var bytes: ByteArray)

//abstract class InfoChunk: SubChunk() { }
//abstract class SdtaChunk: SubChunk() { }
//abstract class PdtaChunk: SubChunk() { }
//
//data class IfilChunk: InfoChunk() { }
//data class IsngChunk: InfoChunk() { }
//data class InamChunk: InfoChunk() { }
//data class IromChunk: InfoChunk() { }
//data class IverChunk: InfoChunk() { }
//data class IcrdChunk: InfoChunk() { }
//data class IengChunk: InfoChunk() { }
//data class IprdChunk: InfoChunk() { }
//data class IcopChunk: InfoChunk() { }
//data class IcmtChunk: InfoChunk() { }
//data class IsftChunk: InfoChunk() { }
//
//data class SmplChunk: SdtaChunk() { }
//data class Sm24Chunk: SdtaChunk() { }
//
//data class PhdrChunk: PdtaChunk() {}
//data class PbagChunk: PdtaChunk() {}
//data class PmodChunk: PdtaChunk() {}
//data class PgenChunk: PdtaChunk() {}
//data class InstChunk: PdtaChunk() {}
//data class IbagChunk: PdtaChunk() {}
//data class ImodChunk: PdtaChunk() {}
//data class IgenChunk: PdtaChunk() {}
//data class ShdrChunk: PdtaChunk() {}

class RiffReader {
    fun from_bytes(bytes: ByteArray): Riff {
        var fourcc: String = ""
        var size = 0
        var typecc: String = ""
        for (i in 0 until 4) {
            fourcc = "$fourcc${bytes[i].toInt().toChar()}"
            typecc = "$typecc${bytes[i + 8].toInt().toChar()}"
            size *= 256
            size += bytes[7 - i].toInt()
        }

        if  (fourcc != "RIFF") {
            throw Exception("Invalid RIFF")
        }

        if (size % 2 == 1) {
            size += 1
        }

        var next_bytes = ByteArray(size)
        for (i in 0 until size) {
            next_bytes[i] = bytes[i + 12]
        }


        return Riff(typecc, this.get_list_chunks(next_bytes))
    }

    fun get_list_chunks(bytes: ByteArray): List<ListChunk> {
        var output: MutableList<ListChunk> = mutableListOf()
        var current_offset = 0
        while (current_offset < bytes.size) {
            var fourcc: String = ""
            var typecc: String = ""
            var size = 0
            for (i in 0 until 4) {
                fourcc = "$fourcc${bytes[i + current_offset].toInt().toChar()}"
                typecc = "$typecc${bytes[current_offset + i + 8].toInt().toChar()}"
                size *= 256
                size += bytes[current_offset + 7 - i].toInt()
            }

            if (fourcc != "LIST") {
                throw Exception("Invalid LIST Chunk")
            }

            var next_bytes = ByteArray(size)
            for (i in 0 until size) {
                next_bytes[i] = bytes[i + 12]
            }

            output.add(ListChunk(typecc, this.get_sub_chunks(next_bytes)))

            size += (size % 2) // consider padding
            current_offset += size
        }

        return output
    }

    fun get_sub_chunks(bytes: ByteArray): List<SubChunk> {
        var output: MutableList<SubChunk> = mutableListOf()
        var current_offset = 0
        while (current_offset < bytes.size) {
            var fourcc: String = ""
            var size = 0
            for (i in 0 until 4) {
                fourcc = "$fourcc${bytes[i + current_offset].toInt().toChar()}"
                size *= 256
                size += bytes[current_offset + 7 - i].toInt()
            }

            var next_bytes = ByteArray(size)
            for (i in 0 until size) {
                next_bytes[i] = bytes[i + 12]
            }

            output.add(SubChunk(fourcc, next_bytes))

            size += (size % 2) // consider padding
            current_offset += size
        }
        return output
    }

}
