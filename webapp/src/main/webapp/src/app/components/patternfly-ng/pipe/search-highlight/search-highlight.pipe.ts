import { Pipe, PipeTransform } from '@angular/core';

/**
 * Search highlight pipe
 *
 * This is currently used with the type ahead feature of the filter fields component
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { SearchHighlightPipeModule } from 'patternfly-ng/pipe';
 * // Or
 * import { SearchHighlightPipeModule } from 'patternfly-ng';
 *
 * &#64;NgModule({
 *   imports: [SearchHighlightPipeModule,...]
 * })
 * export class AppModule(){}
 * </pre></code>
 */
@Pipe({ name: 'searchHighlight' })
export class SearchHighlightPipe implements PipeTransform {
  /**
   * Transform the substring matching the given search
   *
   * @param {string} val The string to highlight
   * @param {string} search The text to search for
   * @returns {any} The given string with highlighted text
   */
  transform(val: string, search: string): any {
    if (search !== undefined && search.length > 0) {
      let lowerVal = val.toLowerCase();
      search = search.toLowerCase();
      if (!lowerVal) return '';
      else return this.convertToOriginal(lowerVal.split(search).join('<b>' + search + '</b>'), val);
    } else {
      return val;
    }
  }

  private convertToOriginal(str: string, original: string): string {
    let output = '';
    let inTag = false;
    let j = 0;
    for (let i = 0; i < str.length; i++) {
      if (str[i] === '<') {
        inTag = true;
        output += str[i];
      } else if (str[i] === '>') {
        inTag = false;
        output += str[i];
      } else if (!inTag) {
        output += original[j++];
      } else {
        output += str[i];
      }
    }
    return output;
  }
}
