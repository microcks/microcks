import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

import { Action } from '../../action/action';
import { Notification } from '../notification';
import { NotificationType } from '../notification-type';

/**
 * Notification service used to notify user about important events in the application.
 *
 * You may configure the service with: setDelay, setVerbose and setPersist.
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { NotificationServiceModule } from 'patternfly-ng/notification';
 * // Or
 * import { NotificationServiceModule } from 'patternfly-ng';
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import { Notification, NotificationType } from 'patternfly-ng/notification';
 * </pre></code>
 */
@Injectable()
export class NotificationService {

  // time (in ms) the notifications are shown
  private delay: number = 8000;
  private modes: any = {};
  private notifications: any = {};
  private persist: any = { 'error': true, 'httpError': true };
  private verbose: boolean = false;
  private _notificationsSubject: Subject<Notification[]> = new Subject();

  /**
   * The default constructor
   */
  constructor() {
    this.notifications.data = [] as Notification[];
    this.modes = [
      { info: { type: NotificationType.INFO, header: 'Info!', log: 'info' } },
      { success: { type: NotificationType.SUCCESS, header: 'Success!', log: 'info' } },
      { error: { type: NotificationType.DANGER, header: 'Error!', log: 'error' } },
      { warn: { type: NotificationType.WARNING, header: 'Warning!', log: 'warn' } }
    ];
    this.modes.forEach((mode: any, index: number) => {
      this.notifications[index] = this.createNotifyMethod(index);
    });
  }

  /**
   * Get all notifications
   */
  getNotifications(): Notification[] {
    return this.notifications.data;
  }

  /**
   * Allows for interacting with a stream of notifications
   */
  get getNotificationsObserver(): Observable<Notification[]> {
    return this._notificationsSubject.asObservable();
  }

  /**
   * Generate a notification for the given HTTP Response
   *
   * @param message The notification message
   * @param httpResponse The HTTP Response
   */
  httpError(message: string, httpResponse: any): void {
    message += ' (' + (httpResponse.data.message || httpResponse.data.cause
      || httpResponse.data.cause || httpResponse.data.errorMessage) + ')';
    this.message('danger', 'Error!', message, this.persist.httpError, undefined, undefined);
    if (this.verbose) {
      console.log(message);
    }
  }

  /**
   * Generate a notification message
   *
   * @param type The notification type
   * @param header The notification header
   * @param message The notification message
   * @param isPersistent True if the notification should be persistent
   * @param primaryAction The primary action for the notifiaction
   * @param moreActions More actions for the kebab
   */
  message(
    type: string,
    header: string,
    message: string,
    isPersistent: boolean,
    primaryAction?: Action,
    moreActions?: Action[]): void {
      let notification = {
        header: header,
        isPersistent: isPersistent,
        isViewing: false,
        message: message,
        moreActions: moreActions,
        primaryAction: primaryAction,
        showClose: false,
        type: type,
        visible: true
      } as Notification;

      this.notifications.data.push(notification);
      this.updateNotificationsStream();

      if (notification.isPersistent !== true) {
        notification.isViewing = false;
        setTimeout(() => {
          notification.visible = false;
          if (!notification.isViewing) {
            this.remove(notification);
          }
        }, this.delay);
      }
    }

  /**
   * Remove notification
   *
   * @param notification The notification to remove
   */
  remove(notification: Notification): void {
    let index = this.notifications.data.indexOf(notification);
    if (index !== -1) {
      this.removeIndex(index);
      this.updateNotificationsStream();
    }
  }

  /**
   * Set the delay after which the notification is dismissed. The argument of this method expects miliseconds. Default
   * delay is 8000 ms.
   *
   * @param delay The delay in ms
   */
  setDelay(delay: number): void {
    this.delay = delay;
  }

  /**
   * Sets persist option for particular modes. Notification with persistent mode won't be dismissed after delay, but has
   * to be closed manually with the close button. By default, the "error" and "httpError" modes are set to persistent.
   *
   * @param persist Set to true to persist notifications
   */
  setPersist(persist: boolean): void {
    this.persist = persist;
  }

  /**
   * Set the verbose mode to on (default) or off. During the verbose mode, each notification is printed in the console.
   *
   * @param verbose Set to true for verbose mode
   */
  setVerbose(verbose: boolean): void {
    this.verbose = verbose;
  }

  /**
   * Set a flag indicating user is viewing the given notification
   *
   * @param notification The notification currently being viewed
   * @param isViewing True if the notification is being viewed
   */
  setViewing(notification: Notification, isViewing: boolean): void {
    notification.isViewing = isViewing;
    if (isViewing !== true && notification.visible !== true) {
      this.remove(notification);
    }
  }

  // Private

  private createNotifyMethod(index: number): any {
    return (message: string, header: string, persistent: boolean, primaryAction: Action, moreActions: Action[]) => {
      if (header !== undefined) {
        header = this.modes[index].header;
      }
      if (persistent !== undefined) {
        persistent = this.persist[index];
      }
      this.notifications.message(this.modes[index].type, header, message, persistent, primaryAction, moreActions);
      if (this.verbose) {
        console.log(message);
      }
    };
  }

  private removeIndex(index: number): void {
    this.notifications.data.splice(index, 1);
  }

  private updateNotificationsStream(): void {
    this._notificationsSubject.next(this.getNotifications());
  }

}
