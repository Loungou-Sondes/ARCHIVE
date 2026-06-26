package ommp.archives.assistant;

import org.springframework.stereotype.Component;

@Component
public class AssistantFaqKnowledge {

	public String answer(String topic) {
		return switch (topic) {
			case "bordereau" -> """
				Comment utiliser les bordereaux de transfert :
				1. Menu « Administration » → « Bordereau de transfert ».
				2. L'agent clique sur « Nouveau bordereau », renseigne les boîtes à transférer et soumet.
				3. L'administrateur valide l'affectation aux emplacements physiques (épis / blocs).
				4. Suivez l'état des bordereaux dans la liste ou via les alertes.""";
			case "conservation" -> """
				Comment utiliser les règles de conservation :
				• Chaque type de document a une durée de conservation (actif → semi-actif → destruction ou transfert définitif).
				• Menu « Paramétrage » → « Règles de conservation » pour créer ou modifier les règles.
				• Le système génère des alertes quand une boîte approche de son échéance ou passe en semi-actif.
				• Menu « Archives & Alertes » → « Alertes et échéances » pour traiter ces cas.
				• Demandez « combien de boîtes semi-actif ? » ou « combien d'alertes ? » pour les chiffres en base.""";
			case "alertes" -> """
				Comment utiliser les alertes :
				• La cloche en haut à droite affiche le total d'alertes.
				• Menu « Archives & Alertes » → « Alertes et échéances » pour le détail :
				  – bordereaux en attente d'affectation ;
				  – boîtes semi-actif inconnue ;
				  – échéances destruction / transfert ;
				  – lignes d'épi presque pleines.
				• Demandez « combien d'alertes ? » pour un résumé chiffré depuis la base.""";
			case "emplacement" -> """
				Comment utiliser les emplacements :
				• Menu « Paramétrage » → « Emplacement ».
				• Vue matricielle : occupation des épis, blocs et tablettes.
				• Vue 3D : visualisation interactive des boîtes sur un épi.
				• Recherche intelligente sur un épi : décrivez une boîte, les emplacements correspondants s'allument en bleu.""";
			case "recherche" -> """
				Comment rechercher des boîtes (depuis Consultation, pas dans ce chat) :
				• Menu « Consultation » → « Recherche des boîtes » : filtres multicritères (titre, mots-clés, année, direction…).
				• Depuis « Emplacement » : recherche intelligente par description naturelle avec surlignage 3D.
				• Si vous ne trouvez rien : essayez d'autres mots-clés, une année ou un type de document.
				• Je peux vous expliquer la procédure, mais la recherche se fait dans le module Consultation.""";
			case "compte" -> """
				Comptes agents :
				• Les agents se connectent avec identifiant et mot de passe OMMP.
				• Mot de passe oublié : réinitialisez le mot de passe depuis « Gestion des agents » (icône clé).
				• Les agents consultent leurs notifications via la cloche en haut à droite.""";
			case "agents" -> """
				Comment gérer les agents :
				• Menu « Gestion Administrative » → « Gestion des agents ».
				• Activez ou désactivez un compte, consultez le profil.
				• Réinitialisez le mot de passe d'un agent depuis « Gestion des agents » (icône clé).""";
			case "archives" -> """
				Archives intermédiaires et historique :
				• « Archives intermédiaires » : boîtes en cours de conservation, consultables par direction.
				• « Historique » : trace des opérations passées (consultation, transferts, validations).
				• Les deux se trouvent dans le menu « Archives & Alertes ».""";
			case "app" -> """
				Navigation dans l'application :
				• Tableau de bord : indicateurs globaux.
				• Administration : bordereaux de transfert.
				• Paramétrage : types de documents, règles de conservation, emplacements.
				• Archives & Alertes : historique, archives intermédiaires, alertes.
				• Consultation : recherche de boîtes et cet assistant IA (guide + données).
				Posez-moi « aide bordereau », « combien de boîtes semi-actif ? », etc.""";
			default -> """
				Je suis votre guide IA pour l'application des archives OMMP :
				• expliquer les modules (« aide bordereau », « aide conservation », « aide alertes ») ;
				• répondre sur les données en base (« combien de boîtes semi-actif ? », « combien d'alertes ? », « mes statistiques »).
				La recherche de boîtes se fait dans Consultation → Recherche des boîtes.
				Que souhaitez-vous faire ?""";
		};
	}
}
