package ommp.archives.assistant.ollama;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import ommp.archives.config.AssistantProperties;

@Component
public class OllamaClient {

	private final AssistantProperties properties;
	private final RestClient restClient;

	public OllamaClient(AssistantProperties properties) {
		this.properties = properties;
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(properties.ollama().timeoutSeconds()));
		this.restClient = RestClient.builder()
			.baseUrl(properties.ollama().baseUrl())
			.requestFactory(factory)
			.build();
	}

	public boolean isEnabled() {
		return properties.ollama().enabled();
	}

	public boolean isAvailable() {
		if (!isEnabled()) {
			return false;
		}
		try {
			restClient.get().uri("/api/tags").retrieve().toBodilessEntity();
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

	public JsonNode chat(List<Map<String, Object>> messages, List<Map<String, Object>> tools) {
		Map<String, Object> body = Map.of(
			"model", properties.ollama().model(),
			"stream", false,
			"messages", messages,
			"tools", tools,
			"options", Map.of("temperature", 0.2)
		);
		return restClient.post()
			.uri("/api/chat")
			.body(body)
			.retrieve()
			.body(JsonNode.class);
	}
}
