import { Action } from '../action/action';

/**
 * An object containing properties for notification messages
 */
export class Notification {
  /**
   * The header to display for the notification
   */
  header?: string;

  /**
   * Flag to show close button for the notification even if showClose is false
   */
  isPersistent?: boolean;

  /**
   * Flag indicating user is actively viewing notification
   */
  isViewing?: boolean;

  /**
   * The main text message of the notification
   */
  message!: string;

  /**
   * More actions to show in a kebab menu
   */
  moreActions?: Action[];

  /**
   * The primary action for the notification
   */
  primaryAction?: Action;

  /**
   * Flag to show the close button on all notifications (not shown with menu actions)
   */
  showClose?: boolean;

  /**
   * The type of the notification message (e.g., 'success', 'info', 'danger', 'warning')
   */
  type!: string;

  /**
   * Flag indicating notification should be visible
   */
  visible?: boolean;

  /**
   * Only for notification drawer module, to dislay time stamp
   */
  timeStamp?: number;
}
