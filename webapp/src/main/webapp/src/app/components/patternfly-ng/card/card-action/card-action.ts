/**
 * An object containing card action properties
 */
export class CardAction {
  /**
   * Set to true to disable the action
   */
  disabled?: boolean;

  /**
   * The action link text
   */
  hypertext!: string;

  /**
   * The action id
   */
  id?: string;

  /**
   * Style class for main icon (e.g., 'fa fa-flag')
   */
  iconStyleClass?: string;

  /**
   * The action link URL
   */
  url?: string;
}
