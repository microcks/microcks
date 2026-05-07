import {
  Component,
  DoCheck,
  EventEmitter,
  Input,
  OnInit,
  Output,
  ViewEncapsulation
} from '@angular/core';
import { CommonModule } from '@angular/common';

import { Action } from '../../action/action';
import { Notification } from '../notification';
import { NotificationEvent } from '../notification-event';

/**
 * Toast notifications are used to notify users of a system occurrence. Toast notifications should be transient and stay
 * on the screen for 8 seconds, so that they do not block the information behind them for too long, but allows the user
 * to read the message. The ToastNotification component allows status, header, message, primary action and menu actions
 * for the notification. The notification can also allow the user to close the notification.
 *
 * Note: Using the kebab menu (more actions) with the close button is not currently supported. If both are specified the
 * close button will not be shown. Add a close menu item if you want to have both capabilities.
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { ToastNotificationModule } from 'patternfly-ng/notification';
 * // Or
 * import { ToastNotificationModule } from 'patternfly-ng';
 *
 * // NGX Bootstrap
 * import { BsDropdownConfig, BsDropdownModule } from 'ngx-bootstrap/dropdown';
 *
 * &#64;NgModule({
 *   imports: [ToastNotificationModule, BsDropdownModule.forRoot(),...],
 *   providers: [BsDropdownConfig]
 * })
 * export class AppModule(){}
 * </pre></code>
 *
 * Optional:
 * <code><pre>
 * import { Notification, NotificationEvent, NotificationType } from 'patternfly-ng/notification';
 * </pre></code>
 */
@Component({
  encapsulation: ViewEncapsulation.None,
  selector: 'pfng-toast-notification',
  templateUrl: './toast-notification.component.html',
  imports: [
    CommonModule,
  ]
})
export class ToastNotificationComponent implements DoCheck, OnInit {
  /**
   * The notification header
   */
  @Input() header?: string;

  /**
   * The notification message
   */
  @Input() message!: string;

  /**
   * The notification kebab actions
   */
  @Input() moreActions?: Action[];

  /**
   * An object containing notifications properties
   */
  @Input() notification!: Notification;

  /**
   * The primary action
   */
  @Input() primaryAction?: Action;

  /**
   * Set to true to show close button
   */
  @Input() showClose?: boolean;

  /**
   * The notification type (e.g., NotificationType.SUCCESS, NotificationType.INFO, etc.)
   */
  @Input() type!: string;

  /**
   * The event emitted when an action has been selected
   */
  @Output('onActionSelect') onActionSelect = new EventEmitter();

  /**
   * The event emitted when the close button has been selected
   */
  @Output('onCloseSelect') onCloseSelect = new EventEmitter();

  /**
   * The event emitted when the mouse hovers over and leaves a notification
   */
  @Output('onViewingChange') onViewingChange = new EventEmitter();

  private _showCloseButton: boolean = false;

  /**
   * The default constructor
   */
  constructor() {
  }

  // Initialization

  /**
   * Setup component configuration upon initialization
   */
  ngOnInit(): void {
  }

  /**
   * Check if the component config has changed
   */
  ngDoCheck(): void {
    this._showCloseButton = (this.showClose === true)
      && (this.moreActions === undefined || this.moreActions === null || this.moreActions.length === 0);
  }

  // Accessors

  /**
   * Get the flag indicating that the close button should be shown
   *
   * @returns {FilterField} The flag indicating that the close button should be shown
   */
  get showCloseButton(): boolean {
    return this._showCloseButton;
  }

  // Actions

  handleEnter($event: MouseEvent): void {
    this.onViewingChange.emit({
      notification: this.notification,
      isViewing: true
    } as NotificationEvent);
  }

  handleLeave($event: MouseEvent): void {
    this.onViewingChange.emit({
      notification: this.notification,
      isViewing: false
    } as NotificationEvent);
  }

  // Private

  protected handleAction(action: Action): void {
    if (action && action.disabled !== true) {
      this.onActionSelect.emit({
        action: action,
        notification: this.notification
      } as NotificationEvent);
    }
  }

  protected handleClose($event: MouseEvent): void {
    this.onCloseSelect.emit({ notification: this.notification } as NotificationEvent);
  }
}
