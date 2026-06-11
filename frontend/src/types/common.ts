export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
