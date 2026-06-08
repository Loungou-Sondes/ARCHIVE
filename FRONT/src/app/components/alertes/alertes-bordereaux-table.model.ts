export type AlertesBordereauxMode = 'validation' | 'pending';

export interface AlertesBordereauxTableConfig {
  mode: AlertesBordereauxMode;
  apiPath: 'validation-agents' | 'en-attente';
  titleKey: string;
  hintKey: string;
  searchKey: string;
  countZeroKey: string;
  countOneKey: string;
  countManyKey: string;
  emptyKey: string;
  loadErrorKey: string;
  sectionExtraClass: string;
  iconClass: string;
  badgeClass: string;
  icon: string;
  deleteDialogMessageKey: string;
}

export interface AlertesBordereauRow {
  id: number;
  numeroBordereau: string;
  dateTransfert: string;
  directionId: string | null;
  directionLabel: string | null;
  agentUserName: string;
  boitesCount: number;
  observation: string | null;
}

export const VALIDATION_BORDEREAUX_CONFIG: AlertesBordereauxTableConfig = {
  mode: 'validation',
  apiPath: 'validation-agents',
  titleKey: 'alertes.validationTitle',
  hintKey: 'alertes.validationHint',
  searchKey: 'alertes.validationSearch',
  countZeroKey: 'alertes.validationCountZero',
  countOneKey: 'alertes.validationCountOne',
  countManyKey: 'alertes.validationCountMany',
  emptyKey: 'alertes.validationEmpty',
  loadErrorKey: 'alertes.loadValidationError',
  sectionExtraClass: 'alertes-section--validation',
  iconClass: 'alertes-section__icon--emerald',
  badgeClass: 'alertes-section__badge--emerald',
  icon: 'fa-solid fa-clipboard-check',
  deleteDialogMessageKey: 'alertes.deletePendingAgentMessage',
};

export const PENDING_BORDEREAUX_CONFIG: AlertesBordereauxTableConfig = {
  mode: 'pending',
  apiPath: 'en-attente',
  titleKey: 'alertes.pendingTitle',
  hintKey: 'alertes.pendingHint',
  searchKey: 'alertes.pendingSearch',
  countZeroKey: 'alertes.pendingCountZero',
  countOneKey: 'alertes.pendingCountOne',
  countManyKey: 'alertes.pendingCountMany',
  emptyKey: 'alertes.pendingEmpty',
  loadErrorKey: 'alertes.loadPendingError',
  sectionExtraClass: 'alertes-section--pending',
  iconClass: 'alertes-section__icon--indigo',
  badgeClass: 'alertes-section__badge--indigo',
  icon: 'pi pi-clock',
  deleteDialogMessageKey: 'alertes.deletePendingMessage',
};
