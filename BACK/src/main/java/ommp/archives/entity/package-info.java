/**
 * Entités JPA = tables Oracle du module archives.
 * <p>
 * Chaque classe {@code @Entity} documente sa table, PK, colonnes et FK dans son Javadoc et sur les champs.
 * <p>
 * Tables principales :
 * <ul>
 *   <li>{@link ommp.archives.entity.Bordereau} — {@code BORDEREAUX}</li>
 *   <li>{@link ommp.archives.entity.Boite} — {@code BOITES}</li>
 *   <li>{@link ommp.archives.entity.BoiteEtat} — {@code ETATS}</li>
 *   <li>{@link ommp.archives.entity.Emplacement} — {@code EMPLACEMENTS}</li>
 *   <li>{@link ommp.archives.entity.ConservationRule} — {@code REGLES_CONSERVATION}</li>
 *   <li>{@link ommp.archives.entity.DocumentType} — {@code DOCUMENT_TYPES}</li>
 *   <li>{@link ommp.archives.entity.Direction} — {@code DIRECTION}</li>
 *   <li>{@link ommp.archives.entity.UserAccount} — {@code USERS}</li>
 *   <li>{@link ommp.archives.entity.UserDetail} — {@code USER_DETAILS}</li>
 *   <li>{@link ommp.archives.entity.Dossier} — {@code DOSSIERS}</li>
 *   <li>Alertes agent bordereau validé — {@code BORDEREAUX.NOTIF_AGENT_VUE} (pas de table {@code NOTIFICATIONS})</li>
 *   <li>{@link ommp.archives.entity.AuditLog} — {@code AUDIT_LOG}</li>
 * </ul>
 */
package ommp.archives.entity;
