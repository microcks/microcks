import { Action } from './action';

/**
 * An action config containing properties for primary and secondary actions such as
 * multiple buttons and kebab menu options
 */
export class ActionConfig {
  /**
   * A list of secondary actions (e.g., menu options for a kebab)
   */
  moreActions?: Action[];

  /**
   * Text announced to screen readers for the action config button
   */
  moreActionsAriaLabel?: string;
  
  /**
   * Set to true to disable secondary actions
   */
  moreActionsDisabled!: boolean;

  /**
   * Style class for the secondary actions container
   */
  moreActionsStyleClass!: string;

  /**
   * Set to false to hide secondary actions
   */
  moreActionsVisible!: boolean;

  /**
   * List of primary actions (e.g., for buttons)
   */
  primaryActions!: Action[];
}
