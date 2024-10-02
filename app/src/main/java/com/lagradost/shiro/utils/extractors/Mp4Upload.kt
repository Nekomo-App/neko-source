package com.lagradost.shiro.utils.extractors

import com.lagradost.shiro.utils.ExtractorApi
import com.lagradost.shiro.utils.ExtractorLink
import com.lagradost.shiro.utils.Qualities
import com.lagradost.shiro.utils.getAndUnpack
import com.lagradost.shiro.utils.mvvm.logError
import khttp.get // Assuming the library is imported for HTTP requests

class Mp4Upload : ExtractorApi() {
    override val name: String = "Mp4Upload"
    override val mainUrl: String = "https://www.mp4upload.com"
    private val srcRegex = Regex("""player\.src\("(.*?)"""")
    override val requiresReferer = true

    override fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        return try {
            val response = get(url) // No headers provided, assuming it's correct for this case
            if (response.text == "File was deleted") return null

            val unpackedText = getAndUnpack(response.text)
            unpackedText?.let {
                srcRegex.find(it)?.groupValues?.get(1)?.let { link ->
                    listOf(
                        ExtractorLink(
                            name,
                            link,
                            url,
                            Qualities.Unknown.value,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logError(e)
            null
        }
    }
}