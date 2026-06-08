export interface BoiteConsultationDetail {
  boiteId: number;
  titre: string;
  motsCles: string | null;
  contenu: string | null;
  anneeMin: number;
  anneeMax: number;
  metrageCm: number;
  documentTypeTitle: string | null;
  bordereauId: number | null;
  numeroBordereau: string | null;
  directionLabel: string | null;
  nombreBoitesBordereau: number;
  typeEtat: string | null;
  typeEtatLabel: string | null;
  emplacement: string | null;
}

export interface Dossier {
  id: number;
  boiteId: number;
  titre: string;
  contenu: string | null;
  annee: number;
  createdAt: string | null;
}

export interface CreateDossierRequest {
  titre: string;
  contenu: string | null;
  annee: number;
}
