package ommp.archives.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import ommp.archives.assistant.AssistantFaqKnowledge;
import ommp.archives.assistant.AssistantIntent;
import ommp.archives.assistant.AssistantIntentDetector;
import ommp.archives.assistant.AssistantRequestContext;
import ommp.archives.assistant.AssistantSuggestionService;
import ommp.archives.assistant.AssistantToolExecutor;
import ommp.archives.assistant.ollama.AssistantOllamaService;
import ommp.archives.dto.assistant.AssistantChatResponse;
import ommp.archives.security.AuthorizationService;

/**
 * Assistant conversationnel local : guide IA + données métier (Ollama avec repli sur moteur à règles).
 * Réservé aux administrateurs.
 */
@Service
public class AssistantChatService {

	private static final Logger log = LoggerFactory.getLogger(AssistantChatService.class);

	private final AssistantIntentDetector intentDetector;
	private final AssistantFaqKnowledge faqKnowledge;
	private final AssistantSuggestionService suggestionService;
	private final AssistantToolExecutor toolExecutor;
	private final AuthorizationService authorization;
	private final AssistantOllamaService ollamaService;

	public AssistantChatService(
		AssistantIntentDetector intentDetector,
		AssistantFaqKnowledge faqKnowledge,
		AssistantSuggestionService suggestionService,
		AssistantToolExecutor toolExecutor,
		AuthorizationService authorization,
		AssistantOllamaService ollamaService
	) {
		this.intentDetector = intentDetector;
		this.faqKnowledge = faqKnowledge;
		this.suggestionService = suggestionService;
		this.toolExecutor = toolExecutor;
		this.authorization = authorization;
		this.ollamaService = ollamaService;
	}

	public AssistantChatResponse chat(Authentication authentication, String rawMessage) {
		authorization.requireAdmin(authentication);
		if (ollamaService.isAvailable()) {
			try {
				return ollamaService.chat(authentication, rawMessage);
			} catch (Exception ex) {
				log.warn("Ollama indisponible pour cette requête, repli sur le moteur local : {}", ex.getMessage());
			}
		}
		return chatWithRules(authentication, rawMessage);
	}

	private AssistantChatResponse chatWithRules(Authentication authentication, String rawMessage) {
		AssistantRequestContext.open(authentication, true);
		try {
			AssistantIntent intent = intentDetector.detect(rawMessage);
			return switch (intent) {
				case GREETING -> greeting();
				case HELP -> help(rawMessage);
				case STATS -> stats();
				case ALERTS -> alerts();
				case UNKNOWN -> unknown();
			};
		} finally {
			AssistantRequestContext.close();
		}
	}

	private AssistantChatResponse greeting() {
		String reply =
			"Bonjour ! Je vous guide dans l'application des archives OMMP et je peux répondre sur les données en base (boîtes, bordereaux, alertes…).";
		return response(reply, AssistantIntent.GREETING, "general");
	}

	private AssistantChatResponse help(String rawMessage) {
		String topic = intentDetector.helpTopic(rawMessage);
		String reply = faqKnowledge.answer(topic);
		if ("recherche".equals(topic) && !rawMessage.toLowerCase().contains("aide")) {
			reply = "Pour rechercher des boîtes, utilisez le module Consultation (recherche multicritère ou sémantique).\n\n" + reply;
		}
		return response(reply, AssistantIntent.HELP, topic);
	}

	private AssistantChatResponse stats() {
		return response(toolExecutor.getApplicationStats(), AssistantIntent.STATS, "stats");
	}

	private AssistantChatResponse alerts() {
		return response(toolExecutor.getAlertesSummary(), AssistantIntent.ALERTS, "alertes");
	}

	private AssistantChatResponse unknown() {
		return response(
			"Je n'ai pas bien compris. Je peux vous guider dans l'application ou répondre sur les données en base.\n"
				+ "Exemples : « aide conservation », « combien de boîtes semi-actif ? », « combien d'alertes ? ».",
			AssistantIntent.UNKNOWN,
			"general"
		);
	}

	private AssistantChatResponse response(String reply, AssistantIntent intent, String helpTopic) {
		return new AssistantChatResponse(
			reply,
			intent.name(),
			suggestionService.forIntent(intent, helpTopic)
		);
	}
}
