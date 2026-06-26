package ommp.archives.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.assistant")
public record AssistantProperties(Ollama ollama) {

	public AssistantProperties {
		if (ollama == null) {
			ollama = new Ollama(true, "http://127.0.0.1:11434", "qwen2.5:3b", 60);
		}
	}

	public record Ollama(
		boolean enabled,
		String baseUrl,
		String model,
		int timeoutSeconds
	) {
		public Ollama {
			if (baseUrl == null || baseUrl.isBlank()) {
				baseUrl = "http://127.0.0.1:11434";
			}
			if (model == null || model.isBlank()) {
				model = "qwen2.5:3b";
			}
			if (timeoutSeconds <= 0) {
				timeoutSeconds = 60;
			}
		}
	}
}
