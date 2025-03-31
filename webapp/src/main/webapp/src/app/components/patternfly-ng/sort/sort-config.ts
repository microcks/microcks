import { SortField } from './sort-field';

/**
 * A config containing properties for sort
 */
export class SortConfig {
  /**
   * A flag indicating the component is disabled
   */
  disabled?: boolean;

  /**
   * A list of sortable fields
   */
  fields?: SortField[];

  /**
   * True if sort is ascending
   */
  isAscending?: boolean;

  /**
   * True if sort should be shown
   */
  visible?: boolean;
}
