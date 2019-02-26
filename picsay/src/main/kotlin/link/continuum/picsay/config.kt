package link.continuum.picsay

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.flatMap
import koma.matrix.json.MoshiInstance
import java.io.File


fun loadTemplates(path: String): Result<Map<String, Template>, Exception> {
        val adapter = MoshiInstance.moshi.adapter<ConfigData>(ConfigData::class.java)
        try {
            val s = File(path).readText()
            return Result.of(adapter.fromJson(s)).flatMap {c: ConfigData? ->
                c ?.let { Result.of(it.templates) } ?:Result.error(NullPointerException("null config"))
            }
        } catch (e: Exception) {
            return Result.error(e)
        }
    }


class ConfigData(
        val templates: Map<String, Template>
)
