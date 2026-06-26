package ommp.archives.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import ommp.archives.entity.AgentStatusCodes;
import ommp.archives.entity.UserAccount;
import ommp.archives.entity.UserDetail;
import ommp.archives.repository.UserAccountRepository;
import ommp.archives.repository.UserDetailRepository;

/**
 * Provisionne un compte agent de démonstration au démarrage (dev / démo / soutenance).
 * <p>
 * Idempotent : ne recrée rien si le nom d'utilisateur existe déjà.
 * Désactivable : {@code app.seed.demo-agent.enabled=false}.
 */
@Component
public class AgentAccountInitializer implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AgentAccountInitializer.class);

	private final UserAccountRepository userAccountRepository;
	private final UserDetailRepository userDetailRepository;
	private final PasswordEncoder passwordEncoder;

	@Value("${app.seed.demo-agent.enabled:true}")
	private boolean enabled;

	@Value("${app.seed.demo-agent.id:agent-demo-001}")
	private String userId;

	@Value("${app.seed.demo-agent.username:agent.demo}")
	private String userName;

	@Value("${app.seed.demo-agent.password:Agent@2026}")
	private String plainPassword;

	@Value("${app.seed.demo-agent.registration-number:MAT-AGENT-001}")
	private String registrationNumber;

	@Value("${app.seed.demo-agent.email:agent.demo@ommp.local}")
	private String email;

	@Value("${app.seed.demo-agent.first-name:Agent}")
	private String firstName;

	@Value("${app.seed.demo-agent.last-name:Démo}")
	private String lastName;

	public AgentAccountInitializer(
		UserAccountRepository userAccountRepository,
		UserDetailRepository userDetailRepository,
		PasswordEncoder passwordEncoder
	) {
		this.userAccountRepository = userAccountRepository;
		this.userDetailRepository = userDetailRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		if (!enabled) {
			return;
		}
		if (userAccountRepository.findByUserNameIgnoreCase(userName).isPresent()) {
			log.debug("Compte agent de démo déjà présent ({}), seed ignoré.", userName);
			return;
		}

		String matricule = registrationNumber.trim();
		UserDetail detail = userDetailRepository.findByRegistrationNumberNormalized(matricule)
			.orElseGet(() -> {
				UserDetail created = new UserDetail();
				created.setRegistrationNumber(matricule);
				created.setFirstName(firstName);
				created.setLastName(lastName);
				created.setStatusId(AgentStatusCodes.ACTIF);
				return userDetailRepository.save(created);
			});
		if (!AgentStatusCodes.isActive(detail.getStatusId())) {
			detail.setStatusId(AgentStatusCodes.ACTIF);
			userDetailRepository.save(detail);
		}

		UserAccount account = new UserAccount();
		account.setId(userId.trim());
		account.setUserName(userName.trim());
		account.setEmail(email);
		account.setRole("user");
		account.setUserRegistrationNumber(matricule);
		account.setPassword(passwordEncoder.encode(plainPassword));
		userAccountRepository.save(account);

		log.info(
			"Compte agent de démo créé — utilisateur: {}, mot de passe: {}, matricule: {}",
			userName,
			plainPassword,
			matricule
		);
	}
}
