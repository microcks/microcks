/**
 * An object containing card filter properties
 */
export class CardFilter {
  /**
   * The filter id
   */
  id?: string;

  /**
   * True if this filter should be selected by default
   */
  default?: boolean;

  /**
   * The title to display for the filter
   */
  title!: string;

  /**
   * Filter value
   */
  value!: string;
}
