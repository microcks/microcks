import { Filter } from './filter';
import { FilterField } from './filter-field';

/**
 * A config containing properties for filters
 */
export class FilterConfig {
  /**
   * A list of the currently applied filters
   */
  appliedFilters?: Filter[];

  /**
   * A flag indicating the component is disabled
   */
  disabled?: boolean;

  /**
   * A list of filterable fields
   */
  fields!: FilterField[];

  /**
   * The number of results returned after the current applied filters have been applied
   */
  resultsCount?: number;

  /**
   * The number selected items
   */
  selectedCount?: number;

  /**
   * Show the control to save the currently applied filter
   */
  showSaveFilter?: boolean;

  /**
   * The total number of items before any filters have been applied
   */
  totalCount?: number;

  /**
   * The tooltip placement (e.g., bottom, left, top, right)
   */
  tooltipPlacement?: string;
}
