import { ListConfigBase } from '../list-config-base';

/**
 * A config containing properties for list view
 */
export class ListConfig extends ListConfigBase {
  /**
   * Set to true to hide the close button in the expansion area. Default is false
   */
  hideClose?: boolean;

  /**
   * Allow expansion for each list item
   */
  useExpandItems?: boolean;

  /**
   * Show pinned items
   */
  usePinItems?: boolean;
}
