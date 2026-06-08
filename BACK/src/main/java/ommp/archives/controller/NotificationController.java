package ommp.archives.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ommp.archives.dto.notification.NotificationsResponse;
import ommp.archives.service.NotificationService;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	public ResponseEntity<NotificationsResponse> list(Authentication authentication) {
		return ResponseEntity.ok(notificationService.listForCurrentUser(authentication));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> dismissOne(Authentication authentication, @PathVariable Long id) {
		notificationService.dismissOne(authentication, id);
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping
	public ResponseEntity<Void> dismissAll(Authentication authentication) {
		notificationService.dismissAll(authentication);
		return ResponseEntity.noContent().build();
	}
}
