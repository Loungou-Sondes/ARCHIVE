/** Modèles partagés matrice épi (alignés sur les DTO Spring). */

export interface EpiSummary {
  id: string;
  numero: string;
  type: string;
  traversCount: number;
  tabletteRows: number;
  blocsPerTablette: number;
  blocLinearCm: number;
  totalLinearCm: number;
}

export interface BlocBoiteSummary {
  id: number;
  titre: string;
  anneeMin: number;
  anneeMax: number;
  metrageCm: number;
  documentTypeTitle: string | null;
  motsCles: string | null;
  bordereauNumeroAffiche?: string | null;
  bordereauId?: number | null;
  bordereauBoiteCount?: number;
  bordereauBoitesNonContigues?: boolean;
}

export interface BlocDto {
  id: string;
  positionIndex: number;
  numero: string;
  boiteId: number | null;
  boite: BlocBoiteSummary | null;
}

export interface TraversHeaderDto {
  index: number;
  numero: string;
}

export interface TabletteCellDto {
  tabletteId: string;
  rowIndex: number;
  colIndex: number;
  tabletteNumero: string;
  blocs: BlocDto[];
  occupiedCount: number;
  totalCount: number;
}

export interface EpiMatrixDto {
  epi: EpiSummary;
  traversHeaders: TraversHeaderDto[];
  rows: TabletteCellDto[][];
}
