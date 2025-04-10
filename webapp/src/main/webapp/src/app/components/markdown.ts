/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import sanitizeHtml from 'sanitize-html';
import markdownit from 'markdown-it'

export const markdownConverter = {
  makeHtml: (markdown: any) => {
    const md = markdownit({
      html: true,
      linkify: true,
      typographer: true,
      breaks: true,
    })
    const unsafeHtml = md.render(markdown);

    return sanitizeHtml(unsafeHtml, {
      allowedTags: [
        'b',
        'i',
        'strike',
        's',
        'del',
        'em',
        'strong',
        'a',
        'p',
        'h1',
        'h2',
        'h3',
        'h4',
        'ul',
        'ol',
        'li',
        'code',
        'pre',
        'table',
        'thead',
        'tbody',
        'tr',
        'th',
        'td'
      ],
      allowedAttributes: {
        a: ['href', 'target', 'rel']
      },
      allowedSchemes: ['http', 'https', 'mailto'],
      transformTags: {
        a: sanitizeHtml.simpleTransform('a', { rel: 'noopener noreferrer' }, true)
      }
    });
  }
};
