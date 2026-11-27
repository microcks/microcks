<script lang="ts">
  import type { Snippet } from 'svelte';
  import Icon from './Icon.svelte';

  interface Props {
    heading: string;
    hint: Snippet;
    accept?: string;
    multiple?: boolean;
    chooseLabel?: string;
    onFiles: (files: File[]) => void;
    size?: 'md' | 'sm';
    class?: string;
    children?: Snippet;
  }

  let { heading, hint, accept, multiple = true, chooseLabel = 'Choose files', onFiles, size = 'md', class: className, children }: Props = $props();

  const id = $props.id();
  let dragging = $state(false);
  let message = $state('');

  function take(list: FileList | null) {
    const files = list ? Array.from(list) : [];
    if (files.length === 0) return;
    message = `${files.length} ${files.length === 1 ? 'file' : 'files'} ready`;
    onFiles(files);
  }
</script>

<div
  role="group"
  aria-labelledby="{id}-heading"
  class={['drop-zone', className]}
  data-dragging={dragging ? '' : undefined}
  data-size={size === 'sm' ? 'sm' : undefined}
  ondragover={(e) => {
    e.preventDefault();
    dragging = true;
  }}
  ondragleave={(e) => {
    if (!e.currentTarget.contains(e.relatedTarget as Node | null)) dragging = false;
  }}
  ondrop={(e) => {
    e.preventDefault();
    dragging = false;
    take(e.dataTransfer?.files ?? null);
  }}
>
  <span class="drop-zone-icon" aria-hidden="true"><Icon name="upload" size={size === 'sm' ? 28 : 24} /></span>
  <p class="drop-zone-heading" id="{id}-heading">{heading}</p>
  <p class="drop-zone-hint" id="{id}-hint">{@render hint()}</p>
  <input
    {id}
    type="file"
    class="drop-zone-input sr-only"
    {accept}
    {multiple}
    aria-describedby="{id}-heading {id}-hint"
    onchange={(e) => {
      take(e.currentTarget.files);
      e.currentTarget.value = '';
    }}
  />
  <div class="drop-zone-actions">
    <label for={id} class="button drop-zone-choose" data-variant={size === 'sm' ? 'secondary' : 'primary'}>{chooseLabel}</label>
    {@render children?.()}
  </div>
  <p class="drop-zone-status" role="status" data-empty={message ? undefined : ''}>{message}</p>
</div>
