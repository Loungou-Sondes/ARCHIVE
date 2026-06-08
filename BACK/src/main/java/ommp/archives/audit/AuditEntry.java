package ommp.archives.audit;

public record AuditEntry(
	String actionCode,
	String resourceType,
	String detail
) {
}
