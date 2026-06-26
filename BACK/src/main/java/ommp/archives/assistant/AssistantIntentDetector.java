package ommp.archives.assistant;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class AssistantIntentDetector {

	private static final Pattern GREETING = Pattern.compile(
		"^(bonjour|bonsoir|salut|hello|hey|coucou|bonne\\s+journ[eé]e)\\b",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern HELP_REQUEST = Pattern.compile(
		"^(aide|help|assistance|guide)\\b"
			+ "|\\b(comment|explique|d[eé]cris|d[eé]finis|utiliser|utilisation|marche|fonctionne)\\b"
			+ "|\\b(qu['']est[- ]ce|qu['']est|c['']est\\s+quoi)\\b"
			+ "|\\b(besoin\\s+d['']aide|j['']ai\\s+besoin|peux[- ]tu\\s+m['']aider|pouvez[- ]vous\\s+m['']aider)\\b",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern HELP_IMPLICIT = Pattern.compile(
		"\\b(je\\s+ne\\s+trouve\\s+pas|pas\\s+trouv[eé]|n['']arrive\\s+pas|[aà]\\s+trouv[eé]|introuvable"
			+ "|probl[eè]me|bloqu[eé]|ne\\s+marche\\s+pas|ne\\s+fonctionne\\s+pas)\\b"
			+ "|\\b(mot\\s+de\\s+passe|password|connexion|oubli[eé]|r[eé]initialiser|se\\s+connecter|login)\\b"
			+ "|\\b(mes\\s+notifications?|ma\\s+cloche|cloche)\\b",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern APP_TOPIC = Pattern.compile(
		"\\b(bordereau|conservation|regle|semi[- ]actif|[eé]pi|emplacement|recherche|alerte|[eé]ch[eé]ance"
			+ "|agent|menu|application|app|tableau\\s+de\\s+bord|dashboard|historique|archive|transfert|document"
			+ "|compte|profil|notification|mot\\s+de\\s+passe|password|bo[iî]tes?|bordereaux?)\\b",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern SEARCH_VERB = Pattern.compile(
		"\\b(cherche(r|z)?|trouve(r|z)?|recherche(r|z)?|localise(r|z)?|o[uù]\\s+est|montre(-|\\s)?moi|je\\s+veux\\s+(trouver|chercher|localiser))\\b",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern STATS = Pattern.compile(
		"\\b(combien|nombre|total|stats?|statistiques?|donn[eé]es?|chiffres?|indicateurs?)\\b"
			+ "|\\b(r[eé]sum[eé]|synth[eè]se)\\s+(des\\s+)?(donn[eé]es|stats|chiffres|alertes)?\\b"
			+ "|\\b(tableau\\s+de\\s+bord|kpi)\\b",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);
	private static final Pattern ALERTS = Pattern.compile(
		"\\b(combien|nombre|total|statut|r[eé]sum[eé])\\s+(d[''])?(alertes?|[eé]ch[eé]ances?)"
			+ "|\\b(alertes?\\s+(en\\s+cours|actives?)|[eé]ch[eé]ances?\\s+(en\\s+cours|actives?))\\b",
		Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
	);

	public AssistantIntent detect(String rawMessage) {
		String message = normalize(rawMessage);
		if (message.isBlank()) {
			return AssistantIntent.UNKNOWN;
		}
		if (GREETING.matcher(message).find() && message.length() < 40) {
			return AssistantIntent.GREETING;
		}
		if (isHelpRequest(message)) {
			return AssistantIntent.HELP;
		}
		if (ALERTS.matcher(message).find()) {
			return AssistantIntent.ALERTS;
		}
		if (STATS.matcher(message).find() && APP_TOPIC.matcher(message).find()) {
			return AssistantIntent.STATS;
		}
		if (STATS.matcher(message).find()) {
			return AssistantIntent.STATS;
		}
		if (APP_TOPIC.matcher(message).find() && message.endsWith("?")) {
			return AssistantIntent.HELP;
		}
		return AssistantIntent.UNKNOWN;
	}

	public String helpTopic(String rawMessage) {
		String message = normalize(rawMessage);
		if (message.contains("mot de passe") || message.contains("password") || message.contains("connexion")
			|| message.contains("oublie") || message.contains("oublié") || message.contains("reinitialiser")
			|| message.contains("login") || message.contains("connecter")) {
			return "compte";
		}
		if (message.contains("notification") || message.contains("cloche") || message.contains("mes notif")) {
			return "alertes";
		}
		if (message.contains("bordereau") || message.contains("transfert")) {
			return "bordereau";
		}
		if (message.contains("conservation") || message.contains("regle") || message.contains("semi-actif") || message.contains("semi actif")) {
			return "conservation";
		}
		if (message.contains("alerte") || message.contains("echeance") || message.contains("échéance")) {
			return "alertes";
		}
		if (message.contains("epi") || message.contains("épi") || message.contains("emplacement")) {
			return "emplacement";
		}
		if (message.contains("recherche") || message.contains("chercher") || message.contains("trouver")
			|| message.contains("trouve pas") || message.contains("introuvable") || SEARCH_VERB.matcher(message).find()) {
			return "recherche";
		}
		if (message.contains("agent") || message.contains("utilisateur") || message.contains("compte") || message.contains("profil")) {
			return "agents";
		}
		if (message.contains("historique") || message.contains("archive")) {
			return "archives";
		}
		if (message.contains("menu") || message.contains("application") || message.contains(" app") || message.startsWith("app")
			|| message.contains("utiliser") || message.contains("tableau")) {
			return "app";
		}
		return "general";
	}

	private boolean isHelpRequest(String message) {
		if (HELP_REQUEST.matcher(message).find() || HELP_IMPLICIT.matcher(message).find()) {
			return true;
		}
		if (SEARCH_VERB.matcher(message).find()) {
			return true;
		}
		return APP_TOPIC.matcher(message).find() && message.split("\\s+").length <= 5;
	}

	private String normalize(String rawMessage) {
		if (rawMessage == null) {
			return "";
		}
		return rawMessage.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}
}
