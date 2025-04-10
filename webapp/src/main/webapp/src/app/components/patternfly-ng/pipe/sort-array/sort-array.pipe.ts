import { Pipe, PipeTransform } from '@angular/core';

import { orderBy } from 'lodash-es';

/**
 * Sort array pipe
 *
 * This is currently used with the pin feature of the list component
 *
 * Example:
 * <div *ngFor="let item of (items | sortPin: 'name': true)">
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { SortArrayPipeModule } from 'patternfly-ng/pipe';
 * // Or
 * import { SortArrayPipeModule } from 'patternfly-ng';
 *
 * &#64;NgModule({
 *   imports: [SortArrayPipeModule,...]
 * })
 * export class AppModule(){}
 * </pre></code>
 */
@Pipe({ name: 'sortArray'})
export class SortArrayPipe implements PipeTransform {
  /**
   * Sort array by property
   *
   * @param {Array<any>} arr Array to sort
   * @param prop Property name to sort by
   * @param {boolean} descending True to sort descending
   * @returns {any} Returns sorted array
   */
  transform(arr: Array<any>, prop: any, descending: boolean = false): any {
    if (arr === undefined) {
      return arr;
    }
    const sortOrder = descending ? 'desc' : 'asc';
    const sortedArray = orderBy(arr, [prop], [sortOrder]);
    return sortedArray;
  }
}
