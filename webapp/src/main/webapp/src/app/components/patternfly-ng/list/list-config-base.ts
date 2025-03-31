import { EmptyStateConfig } from '../empty-state/empty-state-config';

/**
 * A config containing properties for tree list
 */
export class ListConfigBase {
  /**
   * Handle double clicking (item remains selected on a double click). Default is false
   */
  dblClick?: boolean;

  /**
   * A config containing properties for empty state when no items are available
   */
  emptyStateConfig?: EmptyStateConfig;

  /**
   * Allow multiple item selections
   *
   * Not applicable when dblClick is true. Default is false
   */
  multiSelect?: boolean;

  /**
   * Allow row item selection. Default is false
   */
  selectItems?: boolean;

  /**
   * Show checkbox for selecting items. Default is false
   */
  showCheckbox?: boolean;

  /**
   * Show radio button for selecting items. Default is false
   */
  showRadioButton?: boolean;
}
