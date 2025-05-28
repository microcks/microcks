/**
 * An object containing properties for a filterable query, used when filterType is 'select'
 */
export class FilterQuery {
  /**
   * A unique Id for the filter query
   */
  id?: string;

  /**
   * Filter query value used when filterType is 'select'
   */
  value!: string;

  /**
   * The URL used to show an image
   */
  imageUrl?: string;

  /**
   * Style class used to show an icon (e.g., 'fa fa-bookmark')
   */
  iconStyleClass?: string;

  /**
   * Set to true when a separator should be shown instead of a menu option
   */
  separator?: boolean;

  /**
   * Show the control to delete a filter query
   */
  showDelete?: boolean;

  /**
   * Show the control to delete a filter query confirmation
   */
  showDeleteConfirm?: boolean;
}
