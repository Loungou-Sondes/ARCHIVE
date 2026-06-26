package ommp.archives.dto.assistant;

import java.util.List;

public record AssistantChatResponse(
	String reply,
	String intent,
	List<String> suggestions
) {
}
