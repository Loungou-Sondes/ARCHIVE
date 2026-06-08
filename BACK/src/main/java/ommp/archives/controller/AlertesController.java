package ommp.archives.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ommp.archives.dto.alertes.AlertesCountResponse;
import ommp.archives.service.AlertesCountService;

@RestController
@RequestMapping("/api/alertes")
public class AlertesController {

	private final AlertesCountService alertesCountService;

	public AlertesController(AlertesCountService alertesCountService) {
		this.alertesCountService = alertesCountService;
	}

	@GetMapping("/count")
	public ResponseEntity<AlertesCountResponse> count(Authentication authentication) {
		return ResponseEntity.ok(alertesCountService.getCounts(authentication));
	}
}
