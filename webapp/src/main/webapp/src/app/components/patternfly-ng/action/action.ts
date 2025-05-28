import { TemplateRef } from '@angular/core';

/**
 * An action containing common properties for buttons, kebabs, etc.
 */
export class Action {
  /**
   * Set to true to disable the action
   */
  disabled?: boolean;

  /**
   * Unique Id for the filter field, useful for comparisons
   */
  id?: string;

  /**
   * Set to true if this is a placehodler for a separator rather than an action
   */
  separator?: boolean;

  /**
   * Style class for button or kebab menu option
   */
  styleClass?: string;

  /**
   * Template name for including custom content (primary actions only)
   */
  template?: TemplateRef<any>;

  /**
   * The title for the action, displayed as the button label or kebab menu option
   */
  title?: string;

  /**
   * A tooltip for the action
   */
  tooltip?: string;

  /**
   * Set to false if the button or kebab menu option should be hidden
   */
  visible?: boolean;
}
