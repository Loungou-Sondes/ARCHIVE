package ommp.archives.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ommp.archives.dto.dashboard.DashboardAgentRankDto;
import ommp.archives.dto.dashboard.DashboardChartSliceDto;
import ommp.archives.dto.dashboard.DashboardKpiDto;
import ommp.archives.dto.dashboard.DashboardMonthCountDto;
import ommp.archives.dto.dashboard.DashboardPeriod;
import ommp.archives.dto.dashboard.DashboardStatsResponse;
import ommp.archives.dto.dashboard.AgentDashboardStatsResponse;
import ommp.archives.entity.BoiteEtatType;
import ommp.archives.entity.BordereauStatut;
import ommp.archives.entity.TypeEmp;
import ommp.archives.entity.UserAccount;
import ommp.archives.exception.ApiException;
import ommp.archives.repository.BoiteRepository;
import ommp.archives.repository.BordereauRepository;
import ommp.archives.repository.EmplacementRepository;
import ommp.archives.repository.UserAccountRepository;
import ommp.archives.repository.UserDetailRepository;
import ommp.archives.security.AuthorizationService;

@Service
public class DashboardService {

	private static final Locale FR = Locale.FRENCH;

	private final BordereauRepository bordereauRepository;
	private final BoiteRepository boiteRepository;
	private final EmplacementRepository emplacementRepository;
	private final UserDetailRepository userDetailRepository;
	private final UserAccountRepository userAccountRepository;
	private final AuthorizationService authorization;

	public DashboardService(
		BordereauRepository bordereauRepository,
		BoiteRepository boiteRepository,
		EmplacementRepository emplacementRepository,
		UserDetailRepository userDetailRepository,
		UserAccountRepository userAccountRepository,
		AuthorizationService authorization
	) {
		this.bordereauRepository = bordereauRepository;
		this.boiteRepository = boiteRepository;
		this.emplacementRepository = emplacementRepository;
		this.userDetailRepository = userDetailRepository;
		this.userAccountRepository = userAccountRepository;
		this.authorization = authorization;
	}

	@Transactional(readOnly = true)
	public DashboardStatsResponse getStats(Authentication authentication, DashboardPeriod period) {
		authorization.requireAdmin(authentication);
		DashboardPeriod p = period == null ? DashboardPeriod.CURRENT : period;
		LocalDate today = LocalDate.now();
		DateRange chartRange = chartRangeFor(p, today);
		Long bordereauxSurPeriode = periodCountFor(p, today);

		long blocsTotal = emplacementRepository.countByTypeEmp(TypeEmp.BLOC);
		long blocsOccupes = emplacementRepository.countByTypeEmpAndBoiteIdIsNotNull(TypeEmp.BLOC);
		int occupationPct = blocsTotal == 0 ? 0 : (int) Math.round(100.0 * blocsOccupes / blocsTotal);

		Map<BoiteEtatType, Long> etatCounts = loadGlobalEtatCounts();

		DashboardKpiDto kpis = new DashboardKpiDto(
			bordereauRepository.countByStatut(BordereauStatut.AFFECTE),
			bordereauRepository.countByStatut(BordereauStatut.EN_ATTENTE),
			bordereauxSurPeriode,
			etatCounts.get(BoiteEtatType.SEMI_ACTIF),
			etatCounts.get(BoiteEtatType.TRANSFERT),
			etatCounts.get(BoiteEtatType.DESTRUCTION),
			emplacementRepository.countByTypeEmp(TypeEmp.EPI),
			blocsTotal,
			blocsOccupes,
			occupationPct,
			userDetailRepository.countRegisteredAgents()
		);

		return new DashboardStatsResponse(
			p.name().toLowerCase(),
			kpis,
			buildBoiteSlices(etatCounts),
			buildActivity(chartRange),
			buildAgentRanks()
		);
	}

	private Long periodCountFor(DashboardPeriod period, LocalDate today) {
		return switch (period) {
			case CURRENT -> null;
			case DAYS_30 -> bordereauRepository.countByDateTransfertBetween(today.minusDays(29), today);
			case YTD -> bordereauRepository.countByDateTransfertBetween(
				LocalDate.of(today.getYear(), 1, 1),
				today
			);
		};
	}

	private DateRange chartRangeFor(DashboardPeriod period, LocalDate today) {
		return switch (period) {
			case CURRENT -> new DateRange(today.minusMonths(11).withDayOfMonth(1), today);
			case DAYS_30 -> new DateRange(today.minusDays(29), today);
			case YTD -> new DateRange(LocalDate.of(today.getYear(), 1, 1), today);
		};
	}

	private Map<BoiteEtatType, Long> loadGlobalEtatCounts() {
		Map<BoiteEtatType, Long> counts = new EnumMap<>(BoiteEtatType.class);
		for (BoiteEtatType t : BoiteEtatType.values()) {
			counts.put(t, 0L);
		}
		for (Object[] row : boiteRepository.countGroupByEtatCourantType()) {
			counts.put((BoiteEtatType) row[0], (Long) row[1]);
		}
		return counts;
	}

	private List<DashboardChartSliceDto> buildBoiteSlices(Map<BoiteEtatType, Long> counts) {
		return List.of(
			slice("Semi-actif", "SEMI_ACTIF", counts.get(BoiteEtatType.SEMI_ACTIF)),
			slice(BoiteEtatType.TRANSFERT.displayLabel(), "TRANSFERT", counts.get(BoiteEtatType.TRANSFERT)),
			slice(BoiteEtatType.DESTRUCTION.displayLabel(), "DESTRUCTION", counts.get(BoiteEtatType.DESTRUCTION))
		);
	}

	private static DashboardChartSliceDto slice(String label, String code, long value) {
		return new DashboardChartSliceDto(label, code, value);
	}

	private List<DashboardMonthCountDto> buildActivity(DateRange range) {
		return buildActivity(range, bordereauRepository.countByMonthBetween(range.from(), range.to()));
	}

	private List<DashboardMonthCountDto> buildActivity(DateRange range, List<Object[]> rawRows) {
		List<DashboardMonthCountDto> raw = new ArrayList<>();
		for (Object[] row : rawRows) {
			int year = ((Number) row[0]).intValue();
			int month = ((Number) row[1]).intValue();
			long count = ((Number) row[2]).longValue();
			YearMonth ym = YearMonth.of(year, month);
			String label = ym.getMonth().getDisplayName(TextStyle.SHORT, FR) + " " + year;
			raw.add(new DashboardMonthCountDto(year, month, label, count));
		}
		if (range.from().getYear() == range.to().getYear() && range.from().getMonthValue() == range.to().getMonthValue()) {
			return raw;
		}
		Map<YearMonth, Long> byMonth = new java.util.LinkedHashMap<>();
		YearMonth cursor = YearMonth.from(range.from());
		YearMonth end = YearMonth.from(range.to());
		while (!cursor.isAfter(end)) {
			byMonth.put(cursor, 0L);
			cursor = cursor.plusMonths(1);
		}
		for (DashboardMonthCountDto m : raw) {
			byMonth.put(YearMonth.of(m.year(), m.month()), m.count());
		}
		List<DashboardMonthCountDto> filled = new ArrayList<>();
		for (var e : byMonth.entrySet()) {
			YearMonth ym = e.getKey();
			filled.add(new DashboardMonthCountDto(
				ym.getYear(),
				ym.getMonthValue(),
				ym.getMonth().getDisplayName(TextStyle.SHORT, FR) + " " + ym.getYear(),
				e.getValue()
			));
		}
		return filled;
	}

	private List<DashboardAgentRankDto> buildAgentRanks() {
		List<DashboardAgentRankDto> out = new ArrayList<>();
		for (Object[] row : bordereauRepository.countBordereauxGroupByAgent(PageRequest.of(0, 5))) {
			String userName = row[0] != null ? row[0].toString() : "—";
			long count = ((Number) row[1]).longValue();
			out.add(new DashboardAgentRankDto(userName, count));
		}
		return out;
	}

	@Transactional(readOnly = true)
	public AgentDashboardStatsResponse getAgentStats(Authentication authentication) {
		authorization.requireAuthenticated(authentication);
		String username = authentication.getName();
		UserAccount account = userAccountRepository.findByUserName(username)
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Compte utilisateur introuvable."));
		String agentId = account.getId();

		long affectes = bordereauRepository.countByAgentIdAndStatut(agentId, BordereauStatut.AFFECTE);
		long enAttente = bordereauRepository.countByAgentIdAndStatut(agentId, BordereauStatut.EN_ATTENTE);
		long totalBoites = boiteRepository.countByBordereau_AgentId(agentId);

		Map<BoiteEtatType, Long> etatCounts = new EnumMap<>(BoiteEtatType.class);
		for (BoiteEtatType t : BoiteEtatType.values()) {
			etatCounts.put(t, 0L);
		}
		for (Object[] row : boiteRepository.countGroupByEtatCourantTypeForAgent(agentId)) {
			etatCounts.put((BoiteEtatType) row[0], (Long) row[1]);
		}

		LocalDate today = LocalDate.now();
		LocalDate from = today.minusMonths(5).withDayOfMonth(1);
		List<DashboardMonthCountDto> activity = buildActivity(
			new DateRange(from, today),
			bordereauRepository.countByMonthBetweenForAgent(agentId, from, today)
		);

		String directionLabel = resolveDirectionLabel(account);

		List<DashboardChartSliceDto> bordereauxParStatut = List.of(
			slice("Affectés", "AFFECTE", affectes),
			slice("En attente", "EN_ATTENTE", enAttente)
		);
		List<DashboardChartSliceDto> boitesParEtat = List.of(
			slice("Semi-actif", "SEMI_ACTIF", etatCounts.get(BoiteEtatType.SEMI_ACTIF)),
			slice(BoiteEtatType.TRANSFERT.displayLabel(), "TRANSFERT", etatCounts.get(BoiteEtatType.TRANSFERT)),
			slice(BoiteEtatType.DESTRUCTION.displayLabel(), "DESTRUCTION", etatCounts.get(BoiteEtatType.DESTRUCTION))
		);

		return new AgentDashboardStatsResponse(
			affectes,
			enAttente,
			totalBoites,
			bordereauxParStatut,
			boitesParEtat,
			activity,
			directionLabel
		);
	}

	private String resolveDirectionLabel(UserAccount account) {
		String registration = account.getUserRegistrationNumber();
		if (registration == null || registration.isBlank()) {
			return null;
		}
		return userDetailRepository.findByRegistrationNumber(registration)
			.map(ud -> ud.getDirectionId())
			.filter(d -> d != null && !d.isBlank())
			.orElse(null);
	}

	private record DateRange(LocalDate from, LocalDate to) {
	}
}
