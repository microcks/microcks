import {
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
  ViewEncapsulation
} from '@angular/core';
import { CommonModule } from '@angular/common';

import { Notification } from '../notification';
import { NotificationEvent } from '../notification-event';
import { ToastNotificationComponent } from '../toast-notification/toast-notification.component';

/**
 * Component to display a list of toast notifications
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { ToastNotificationListModule } from 'patternfly-ng/notification';
 * // Or
 * import { ToastNotificationListModule } from 'patternfly-ng';
 *
 * // NGX Bootstrap
 * import { BsDropdownConfig, BsDropdownModule } from 'ngx-bootstrap/dropdown';
 *
 * &#64;NgModule({
 *   imports: [ToastNotificationListModule, BsDropdownModule.forRoot(),...],
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
  selector: 'pfng-toast-notification-list',
  templateUrl: './toast-notification-list.component.html',
  imports: [
    CommonModule,
    ToastNotificationComponent
  ]
})
export class ToastNotificationListComponent implements OnInit {
  /**
   * A list of notifiactions to display
   */
  @Input() notifications!: Notification[];

  /**
   * Set to true to show close button
   */
  @Input() showClose!: boolean;

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

  // Actions

  /**
   * Check if the component config has changed
   */
  protected handleAction($event: NotificationEvent): void {
    this.onActionSelect.emit($event);
  }

  protected handleClose($event: NotificationEvent): void {
    this.onCloseSelect.emit($event);
  }

  protected handleViewingChange($event: NotificationEvent) {
    this.onViewingChange.emit($event);
  }
}
