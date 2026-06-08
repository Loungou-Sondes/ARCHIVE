package ommp.archives.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Oracle : Hibernate {@code ddl-auto=update} n'a pas toujours créé {@code USERS.PASSWORD_RESET_REQUESTED}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserPasswordResetColumnMigration implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(UserPasswordResetColumnMigration.class);

	private final JdbcTemplate jdbcTemplate;

	public UserPasswordResetColumnMigration(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void run(ApplicationArguments args) {
		ensureBooleanColumn("USERS", "PASSWORD_RESET_REQUESTED");
		ensureBooleanColumn("USERS", "PASSWORD_RESET_APPROVED");
	}

	private void ensureBooleanColumn(String tableName, String columnName) {
		if (columnExists(tableName, columnName)) {
			return;
		}
		try {
			jdbcTemplate.execute(
				"ALTER TABLE " + tableName + " ADD (" + columnName + " NUMBER(1) DEFAULT 0 NOT NULL)"
			);
			log.info("Colonne {}.{} créée.", tableName, columnName);
		} catch (Exception oracleErr) {
			try {
				jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD " + columnName + " NUMBER(1) DEFAULT 0");
				jdbcTemplate.execute(
					"UPDATE " + tableName + " SET " + columnName + " = 0 WHERE " + columnName + " IS NULL"
				);
				jdbcTemplate.execute(
					"ALTER TABLE " + tableName + " MODIFY " + columnName + " NUMBER(1) DEFAULT 0 NOT NULL"
				);
				log.info("Colonne {}.{} créée (migration en 3 étapes).", tableName, columnName);
			} catch (Exception fallbackErr) {
				log.error(
					"Impossible de créer {}.{}. Exécutez manuellement : "
						+ "ALTER TABLE " + tableName + " ADD (" + columnName + " NUMBER(1) DEFAULT 0 NOT NULL);",
					fallbackErr
				);
			}
		}
	}

	private boolean columnExists(String tableName, String columnName) {
		try {
			Integer n = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ? AND COLUMN_NAME = ?",
				Integer.class,
				tableName.toUpperCase(),
				columnName.toUpperCase()
			);
			return n != null && n > 0;
		} catch (Exception ignored) {
			return false;
		}
	}
}
