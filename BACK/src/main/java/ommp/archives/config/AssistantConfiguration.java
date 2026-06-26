package ommp.archives.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableConfigurationProperties(AssistantProperties.class)
@PropertySource("classpath:application-assistant.properties")
public class AssistantConfiguration {
}
