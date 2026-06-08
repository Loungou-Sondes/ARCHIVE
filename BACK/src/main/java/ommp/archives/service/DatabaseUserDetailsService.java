package ommp.archives.service;

import java.util.Arrays;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import ommp.archives.entity.AgentStatusCodes;
import ommp.archives.entity.UserAccount;
import ommp.archives.entity.UserDetail;
import ommp.archives.repository.UserAccountRepository;
import ommp.archives.repository.UserDetailRepository;

/**
 * Adaptateur Spring Security : charge un {@link UserAccount} via le repository et retourne un {@link UserDetails}
 * (aucune entité JPA exposée à HTTP).
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

	private final UserAccountRepository userAccountRepository;
	private final UserDetailRepository userDetailRepository;

	public DatabaseUserDetailsService(
		UserAccountRepository userAccountRepository,
		UserDetailRepository userDetailRepository
	) {
		this.userAccountRepository = userAccountRepository;
		this.userDetailRepository = userDetailRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		UserAccount account = userAccountRepository.findByUserName(username)
			.orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable : " + username));
		String[] authorities = parseRoles(account.getRole());
		boolean enabled = isAccountEnabled(account);
		return User.builder()
			.username(account.getUserName())
			.password(account.getPassword())
			.disabled(!enabled)
			.authorities(authorities)
			.build();
	}

	private boolean isAccountEnabled(UserAccount account) {
		String reg = account.getUserRegistrationNumber();
		if (reg == null || reg.isBlank()) {
			return true;
		}
		return userDetailRepository.findByRegistrationNumberNormalized(reg)
			.map(UserDetail::getStatusId)
			.map(AgentStatusCodes::isActive)
			.orElse(true);
	}

	/** Colonne ROLE : valeurs séparées par des virgules ; préfixe ROLE_ ajouté si absent (ex. admin → ROLE_ADMIN). */
	private static String[] parseRoles(String roleColumn) {
		if (roleColumn == null || roleColumn.isBlank()) {
			return new String[] { "ROLE_USER" };
		}
		return Arrays.stream(roleColumn.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.map(DatabaseUserDetailsService::toAuthority)
			.distinct()
			.toArray(String[]::new);
	}

	private static String toAuthority(String raw) {
		String upper = raw.toUpperCase();
		if (upper.startsWith("ROLE_")) {
			return upper;
		}
		return "ROLE_" + upper;
	}
}

