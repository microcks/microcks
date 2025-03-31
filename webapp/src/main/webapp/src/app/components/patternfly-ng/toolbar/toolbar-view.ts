/**
 * An view containing common properties
 */
export class ToolbarView {
  /**
   * True if view is disabled
   */
  disabled?: boolean;

  /**
   * Style class to use for the view selector
   */
  iconStyleClass?: string;

  /**
   * Unique id for the view
   */
  id?: string;

  /**
   * A tooltip for the view selector
   */
  tooltip?: string;

  /**
   * Text announced to screen readers for the action config button
   */
  ariaLabel?: string;
}
