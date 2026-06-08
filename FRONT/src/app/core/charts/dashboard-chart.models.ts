/** Aligné sur {@code DashboardChartSliceDto} / listes dashboard API. */
export interface DashboardChartSlice {
  label: string;
  code: string;
  value: number;
}

/** Aligné sur {@code DashboardMonthCountDto}. */
export interface DashboardMonthCount {
  year: number;
  month: number;
  label: string;
  count: number;
}
