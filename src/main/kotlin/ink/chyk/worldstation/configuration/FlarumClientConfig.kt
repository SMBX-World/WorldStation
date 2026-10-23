package ink.chyk.worldstation.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class FlarumClientConfig {
    @Bean
    fun flarumRestClient(properties: FlarumProperties): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(properties.connectTimeout.toMillis().toInt())
            setReadTimeout(properties.readTimeout.toMillis().toInt())
        }
        return RestClient.builder()
            .baseUrl(properties.baseUrl.trimEnd('/'))
            .requestFactory(requestFactory)
            .build()
    }
}
