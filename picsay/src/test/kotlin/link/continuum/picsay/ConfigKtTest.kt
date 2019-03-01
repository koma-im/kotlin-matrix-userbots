package link.continuum.picsay

import com.github.kittinunf.result.Result
import com.github.kittinunf.result.failure
import com.github.kittinunf.result.flatMap
import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test

class ConfigKtTest {
    @Ignore @Test fun testRun() {
        loadTemplates("config.json").flatMap {
            val text = "test ".repeat(12)
            for ((label, template) in it) {
                println("rendering template $label $template")
                template.render(text).fold({
                    val name = "$label.${template.format}".toLowerCase()
                    println("saving file $name")
                    File(name).writeBytes(it)
                }, {
                    println("rendering failure")
                    it.printStackTrace()
                })

            }
            Result.of(Unit)
        }.failure{
            it.printStackTrace()
        }

    }
}
