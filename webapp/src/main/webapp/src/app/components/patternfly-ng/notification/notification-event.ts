import { Action } from '../action/action';
import { Notification } from './notification';

/**
 * An object containing properties for notification events
 */
export class NotificationEvent {
  /**
   * Configuration properties for notification actions
   */
  action?: Action;

  /**
   * Configuration properties for a notification message
   */
  notification!: Notification;

  /**
   * Flag indicating user is actively viewing notification
   */
  isViewing?: boolean;
}
