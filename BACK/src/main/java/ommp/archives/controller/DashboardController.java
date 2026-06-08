package ommp.archives.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ommp.archives.dto.dashboard.AgentDashboardStatsResponse;
import ommp.archives.dto.dashboard.DashboardPeriod;
import ommp.archives.dto.dashboard.DashboardStatsResponse;
import ommp.archives.service.DashboardService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping("/stats")
	public ResponseEntity<DashboardStatsResponse> stats(
		Authentication authentication,
		@RequestParam(name = "period", required = false, defaultValue = "current") String period
	) {
		return ResponseEntity.ok(
			dashboardService.getStats(authentication, DashboardPeriod.fromParam(period))
		);
	}

	@GetMapping("/agent-stats")
	public ResponseEntity<AgentDashboardStatsResponse> agentStats(Authentication authentication) {
		return ResponseEntity.ok(dashboardService.getAgentStats(authentication));
	}
}
