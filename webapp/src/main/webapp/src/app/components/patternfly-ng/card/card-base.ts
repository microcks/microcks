import {
  Component,
  Input,
  TemplateRef,
} from '@angular/core';

/**
 * Card base component
 *
 * For customization, use the templates named headerTemplate and footerTemplate.
 */
@Component({
  selector: 'pfng-card-base',
  template: ''
})
export abstract class CardBase {
  /**
   * The name of the template containing footer layout
   */
  @Input() footerTemplate?: TemplateRef<any>;

  /**
   * The name of the template containing header layout
   */
  @Input() headerTemplate?: TemplateRef<any>;

  /**
   * The default constructor
   */
  constructor() {
  }
}
