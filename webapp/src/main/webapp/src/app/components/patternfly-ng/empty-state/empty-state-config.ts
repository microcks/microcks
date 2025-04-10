import { ActionConfig } from '../action/action-config';

/**
 * An empty state config containing component properties
 */
export class EmptyStateConfig {
  /**
   * The action config containing button properties
   */
  actions?: ActionConfig;

  /**
   * Config properties for the help link
   */
  helpLink?: {
    /**
     * Help link text
     */
    hypertext: string;

    /**
     * Help link description
     */
    text?: string;

    /**
     * Help link URL
     */
    url: string;
  };

  /**
   * Style class for main icon (e.g., 'pficon pficon-add-circle-o')
   */
  iconStyleClass?: string;

  /**
   * Text for the main informational paragraph
   */
  info?: string;

  /**
   * Text for the main title
   */
  title!: string;
}
