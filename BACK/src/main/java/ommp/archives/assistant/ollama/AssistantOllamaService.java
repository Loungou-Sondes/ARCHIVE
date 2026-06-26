package ommp.archives.assistant.ollama;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ommp.archives.assistant.AssistantIntent;
import ommp.archives.assistant.AssistantIntentDetector;
import ommp.archives.assistant.AssistantRequestContext;
import ommp.archives.assistant.AssistantSuggestionService;
import ommp.archives.assistant.AssistantToolExecutor;
import ommp.archives.dto.assistant.AssistantChatResponse;

@Service
public class AssistantOllamaService {

	private static final int MAX_TURNS = 5;

	private static final String SYSTEM_PROMPT = """
		Tu es l'assistant IA local des archives intermédiaires OMMP.
		Tu guides les utilisateurs dans l'application et réponds sur les données réelles en base.
		Règles strictes :
		- Utilise TOUJOURS un outil avant de répondre sur des chiffres, alertes ou procédures.
		- N'invente JAMAIS de statistique, numéro de boîte, épi ou bordereau.
		- Ne recherche PAS de boîtes : oriente vers Consultation → Recherche des boîtes.
		- Réponds en français, clairement, en 2 à 8 phrases.
		- Si la demande est hors sujet, explique poliment ce que tu peux faire.""";

	private final OllamaClient ollamaClient;
	private final AssistantToolExecutor toolExecutor;
	private final AssistantSuggestionService suggestionService;
	private final AssistantIntentDetector intentDetector;
	private final ObjectMapper objectMapper;

	public AssistantOllamaService(
		OllamaClient ollamaClient,
		AssistantToolExecutor toolExecutor,
		AssistantSuggestionService suggestionService,
		AssistantIntentDetector intentDetector,
		ObjectMapper objectMapper
	) {
		this.ollamaClient = ollamaClient;
		this.toolExecutor = toolExecutor;
		this.suggestionService = suggestionService;
		this.intentDetector = intentDetector;
		this.objectMapper = objectMapper;
	}

	public boolean isAvailable() {
		return ollamaClient.isAvailable();
	}

	public AssistantChatResponse chat(Authentication authentication, String userMessage) {
		AssistantRequestContext.open(authentication, true);
		try {
			List<Map<String, Object>> messages = new ArrayList<>();
			messages.add(message("system", SYSTEM_PROMPT));
			messages.add(message("user", userMessage));

			String lastTool = null;
			String lastHelpTopic = "general";

			for (int turn = 0; turn < MAX_TURNS; turn++) {
				JsonNode response = ollamaClient.chat(messages, toolDefinitions());
				JsonNode assistantMessage = response.path("message");
				if (assistantMessage.isMissingNode()) {
					break;
				}
				messages.add(jsonToMap(assistantMessage));

				JsonNode toolCalls = assistantMessage.path("tool_calls");
				if (toolCalls.isArray() && !toolCalls.isEmpty()) {
					for (JsonNode toolCall : toolCalls) {
						String name = toolCall.path("function").path("name").asText("");
						JsonNode argsNode = toolCall.path("function").path("arguments");
						String toolResult = executeTool(name, argsNode);
						lastTool = name;
						if ("get_application_help".equals(name)) {
							lastHelpTopic = parseTopic(argsNode);
						}
						messages.add(Map.of(
							"role", "tool",
							"content", toolResult,
							"name", name
						));
					}
					continue;
				}

				String content = assistantMessage.path("content").asText("").trim();
				if (!content.isBlank()) {
					AssistantIntent intent = intentFromTool(lastTool, userMessage);
					String topic = "get_application_help".equals(lastTool) ? lastHelpTopic : intentDetector.helpTopic(userMessage);
					return new AssistantChatResponse(
						content,
						intent.name(),
						suggestionService.forIntent(intent, topic)
					);
				}
				break;
			}
			throw new IllegalStateException("Ollama n'a pas produit de réponse exploitable.");
		} finally {
			AssistantRequestContext.close();
		}
	}

	private String executeTool(String name, JsonNode argsNode) {
		return switch (name) {
			case "get_application_stats" -> toolExecutor.getApplicationStats();
			case "get_alertes_summary" -> toolExecutor.getAlertesSummary();
			case "get_application_help" -> toolExecutor.getApplicationHelp(readArg(argsNode, "topic"));
			default -> "Outil inconnu : " + name;
		};
	}

	private AssistantIntent intentFromTool(String tool, String userMessage) {
		if ("get_application_stats".equals(tool)) {
			return AssistantIntent.STATS;
		}
		if ("get_alertes_summary".equals(tool)) {
			return AssistantIntent.ALERTS;
		}
		if ("get_application_help".equals(tool)) {
			return AssistantIntent.HELP;
		}
		return intentDetector.detect(userMessage);
	}

	private String readArg(JsonNode argsNode, String key) {
		if (argsNode == null || argsNode.isMissingNode()) {
			return "";
		}
		try {
			if (argsNode.isTextual()) {
				JsonNode inner = objectMapper.readTree(argsNode.asText());
				return inner.path(key).asText("");
			}
			return argsNode.path(key).asText("");
		} catch (Exception ex) {
			return argsNode.path(key).asText("");
		}
	}

	private String parseTopic(JsonNode argsNode) {
		String topic = readArg(argsNode, "topic");
		return topic.isBlank() ? "general" : topic;
	}

	private Map<String, Object> message(String role, String content) {
		Map<String, Object> msg = new LinkedHashMap<>();
		msg.put("role", role);
		msg.put("content", content);
		return msg;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> jsonToMap(JsonNode node) {
		return objectMapper.convertValue(node, Map.class);
	}

	private List<Map<String, Object>> toolDefinitions() {
		return List.of(
			tool(
				"get_application_stats",
				"Retourne les statistiques réelles depuis la base : bordereaux, boîtes (semi-actif, transfert, destruction), stockage, alertes. Utiliser pour toute question du type « combien de… ».",
				Map.of("type", "object", "properties", Map.of())
			),
			tool(
				"get_alertes_summary",
				"Résumé chiffré des alertes en cours (administrateur).",
				Map.of("type", "object", "properties", Map.of())
			),
			tool(
				"get_application_help",
				"Explique comment utiliser un module de l'application OMMP (guide, pas de recherche de boîtes).",
				Map.of(
					"type", "object",
					"properties", Map.of(
						"topic", Map.of(
							"type", "string",
							"description", "Sujet : bordereau, conservation, alertes, emplacement, recherche, compte, agents, archives, app, general"
						)
					),
					"required", List.of("topic")
				)
			)
		);
	}

	private Map<String, Object> tool(String name, String description, Map<String, Object> parameters) {
		return Map.of(
			"type", "function",
			"function", Map.of(
				"name", name,
				"description", description,
				"parameters", parameters
			)
		);
	}
}
