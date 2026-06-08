package ommp.archives.dto;

import java.util.List;

public record UserInfoResponse(
	String username,
	List<String> roles
) {
}

