package link.continuum.picsay

import kotlin.test.Ignore
import kotlin.test.Test

class ConfigKtTest {
    @Ignore @Test fun testRun() {
        loadTemplates("config.json").fold({
            println("templates $it")
        }, {
            it.printStackTrace()
        })

    }
}
