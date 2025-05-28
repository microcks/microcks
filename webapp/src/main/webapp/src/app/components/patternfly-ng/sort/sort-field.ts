/**
 * An object containing properties for a sortable field, used to select categories of sorting
 */
export class SortField {
  /**
   * A unique Id for the sort field
   */
  id?: string;

  /**
   * The title to display for the sort field
   */
  title?: string;

  /**
   * The sort field type (e.g., 'alpha' or 'numeric' )
   */
  sortType!: string;
}
