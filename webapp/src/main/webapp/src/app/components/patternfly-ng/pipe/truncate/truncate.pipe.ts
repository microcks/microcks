import { Pipe, PipeTransform } from '@angular/core';

/**
 * Truncate pipe
 *
 * This is currently used with the save filter feature of the filter fields component
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { TruncatePipeModule } from 'patternfly-ng/pipe';
 * // Or
 * import { TruncatePipeModule } from 'patternfly-ng';
 *
 * &#64;NgModule({
 *   imports: [TruncatePipeModule,...]
 * })
 * export class AppModule(){}
 * </pre></code>
 */
@Pipe({ name: 'truncate'})
export class TruncatePipe implements PipeTransform {
  /**
   * Truncate given string
   *
   * @param {string} value The string to truncate
   * @param {string} limit The number of characters to truncate the string at
   * @param {string} trail The trailing characters representing truncation
   * @returns {string} The truncated string
   */
  transform(value: string, limit: number = 10, trail: string = '...'): string {
    return (value.length > limit) ? value.substring(0, limit) + trail : value;
  }
}
