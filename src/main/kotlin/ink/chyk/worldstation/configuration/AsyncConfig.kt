package ink.chyk.worldstation.configuration

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.annotation.*
import org.springframework.web.servlet.config.annotation.*
import java.util.concurrent.Executor


@Configuration
@EnableScheduling
class AsyncConfig : AsyncConfigurer {
    fun configureAsyncSupport(configurer: AsyncSupportConfigurer) {
        // 设置异步超时为2小时（适合2GB@4MB/s上传）
        configurer.setDefaultTimeout(7200000)
    }

    @Bean("storageUploadExecutor")
    fun storageUploadExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 2
            queueCapacity = 20
            setThreadNamePrefix("storage-upload-")
            initialize()
        }
    }
}
