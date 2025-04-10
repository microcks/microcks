import { Filter } from './filter';
import { FilterField } from './filter-field';
import { FilterQuery } from './filter-query';

/**
 * An object containing properties for filter events
 */
export class FilterEvent {
  /**
   * A list of the currently applied filters
   */
  appliedFilters?: Filter[];

  /**
   * The currently selected filter field
   */
  field?: FilterField;

  /**
   * The currently selected filter query, if applicable
   */
  query?: FilterQuery;

  /**
   * The filter input field value, if applicable
   */
  value?: string;
}
