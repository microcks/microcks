import { FilterQuery } from './filter-query';

/**
 * An object containing properties for a filterable field, used to select categories of filters
 */
export class FilterField {
  /**
   * A unique Id for the filter field
   */
  id?: string;

  /**
   * Text to display when no filter value has been entered
   */
  placeholder?: string;

  /**
   * A list of filter queries used when filterType is 'select'
   */
  queries?: FilterQuery[];

  /**
   * The title to display for the filter field
   */
  title?: string;

  /**
   * The filter input field type (e.g., 'select' for a select box, 'typeahead' to filter queries)
   */
  type?: string;

  /**
   * Set to true when a separator should be shown instead of a menu option
   */
  separator?: boolean;

  /**
   * A flag indicating the field is disabled
   */
  disabled?: boolean;
}
