package ommp.archives.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import ommp.archives.dto.AgentListPageResponse;
import ommp.archives.dto.AgentResponse;
import ommp.archives.dto.UpdateAgentActiveRequest;
import ommp.archives.service.AgentService;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

	private final AgentService agentService;

	public AgentController(AgentService agentService) {
		this.agentService = agentService;
	}

	@GetMapping
	public ResponseEntity<AgentListPageResponse> list(
		Authentication authentication,
		@RequestParam(required = false) String q,
		@RequestParam(required = false) Boolean passwordResetOnly,
		@PageableDefault(size = 10) Pageable pageable
	) {
		return ResponseEntity.ok(agentService.listAgents(authentication, q, passwordResetOnly, pageable));
	}

	@GetMapping("/{id}")
	public ResponseEntity<AgentResponse> getById(
		Authentication authentication,
		@PathVariable String id
	) {
		return ResponseEntity.ok(agentService.getAgent(authentication, id));
	}

	@PatchMapping("/{id}/active")
	public ResponseEntity<AgentResponse> setActive(
		Authentication authentication,
		@PathVariable String id,
		@Valid @RequestBody UpdateAgentActiveRequest request
	) {
		return ResponseEntity.ok(agentService.setAgentActive(authentication, id, request.active()));
	}
}
