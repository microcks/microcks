import { FilterField } from './filter-field';
import { FilterQuery } from './filter-query';

/**
 * An object containing filter properties
 */
export class Filter {
  /**
   * A filterable field, used to select categories of filters
   */
  field!: FilterField;

  /**
   * A filterable query, if applicable
   */
  query?: FilterQuery;

  /**
   * Filter value
   */
  value!: string;
}
