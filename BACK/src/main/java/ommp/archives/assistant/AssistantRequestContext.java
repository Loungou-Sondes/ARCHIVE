package ommp.archives.assistant;

import org.springframework.security.core.Authentication;

public final class AssistantRequestContext {

	private static final ThreadLocal<Authentication> AUTHENTICATION = new ThreadLocal<>();
	private static final ThreadLocal<Boolean> ADMIN = new ThreadLocal<>();

	private AssistantRequestContext() {
	}

	public static void open(Authentication authentication, boolean admin) {
		AUTHENTICATION.set(authentication);
		ADMIN.set(admin);
	}

	public static Authentication authentication() {
		return AUTHENTICATION.get();
	}

	public static boolean admin() {
		return Boolean.TRUE.equals(ADMIN.get());
	}

	public static void close() {
		AUTHENTICATION.remove();
		ADMIN.remove();
	}
}
