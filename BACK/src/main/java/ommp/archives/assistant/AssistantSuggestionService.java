package ommp.archives.assistant;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class AssistantSuggestionService {

	public List<String> forIntent(AssistantIntent intent, String helpTopic) {
		return switch (intent) {
			case GREETING -> greetingSuggestions();
			case HELP -> helpSuggestions(helpTopic);
			case STATS -> statsSuggestions();
			case ALERTS -> alertsSuggestions();
			case UNKNOWN -> greetingSuggestions();
		};
	}

	private List<String> greetingSuggestions() {
		return List.of("aide conservation", "combien de boîtes semi-actif ?", "combien d'alertes ?");
	}

	private List<String> helpSuggestions(String topic) {
		return switch (topic) {
			case "conservation" -> List.of("combien de boîtes semi-actif ?", "combien d'alertes ?", "aide emplacement");
			case "bordereau" -> List.of("aide conservation", "combien d'alertes ?", "aide emplacement");
			case "alertes" -> List.of("combien d'alertes ?", "aide conservation", "aide bordereau");
			case "emplacement" -> List.of("aide conservation", "combien de boîtes semi-actif ?", "combien d'alertes ?");
			case "recherche" -> List.of("aide bordereau", "aide conservation", "combien d'alertes ?");
			case "compte" -> List.of("aide bordereau", "aide agents", "combien d'alertes ?");
			case "agents" -> List.of("aide alertes", "aide conservation", "combien d'alertes ?");
			case "archives" -> List.of("aide conservation", "combien de boîtes semi-actif ?", "combien d'alertes ?");
			case "app" -> List.of("aide bordereau", "aide conservation", "combien d'alertes ?");
			case "stats" -> statsSuggestions();
			default -> greetingSuggestions();
		};
	}

	private List<String> statsSuggestions() {
		return List.of("combien d'alertes ?", "aide conservation", "aide bordereau");
	}

	private List<String> alertsSuggestions() {
		return List.of("combien de boîtes semi-actif ?", "aide alertes", "aide conservation");
	}
}
