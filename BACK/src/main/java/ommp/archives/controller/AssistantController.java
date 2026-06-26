package ommp.archives.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import ommp.archives.dto.assistant.AssistantChatRequest;
import ommp.archives.dto.assistant.AssistantChatResponse;
import ommp.archives.service.AssistantChatService;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

	private final AssistantChatService assistantChatService;

	public AssistantController(AssistantChatService assistantChatService) {
		this.assistantChatService = assistantChatService;
	}

	@PostMapping("/chat")
	public ResponseEntity<AssistantChatResponse> chat(
		Authentication authentication,
		@Valid @RequestBody AssistantChatRequest request
	) {
		return ResponseEntity.ok(assistantChatService.chat(authentication, request.message()));
	}
}
