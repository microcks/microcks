import { Injectable } from '@angular/core';

function _window(): any {
  // return the global native browser window object
  return window;
}

/**
 * Native window reference
 *
 * Usage:
 * <code><pre>
 * // Individual module import
 * import { WindowReference } from 'patternfly-ng/utilities';
 * // Or
 * import { WindowReference } from 'patternfly-ng';
 */
@Injectable()
export class WindowReference {
  get nativeWindow(): any {
    return _window();
  }
}
