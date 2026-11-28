/*
 * Import jobs (Importers) that produce an Arazzo document: the job records which document it imported
 * (`ImportJob.arazzoDocumentRef`), so the document can name its job and the job its document.
 */

export interface ArazzoJobLike {
  id: string;
  name: string;
  lastImportDate?: number | string;
  arazzoDocumentRef?: { documentId?: string | null; title: string; version: string; unlinkedSources?: string[] } | null;
}

/** The job that imported a document, if one did. */
export function jobOfDocument<J extends ArazzoJobLike>(jobs: readonly J[], documentId: string): J | undefined {
  return jobs.find((j) => j.arazzoDocumentRef?.documentId === documentId);
}

/** "Synced 12 min ago from <job>" for a document a job keeps in sync, "Imported 3 days ago" for a one-off import. */
export function documentOrigin(job: Pick<ArazzoJobLike, 'name' | 'lastImportDate'> | undefined, importedAt: number | undefined, ago: (ms: number) => string): string {
  if (job?.lastImportDate !== undefined) return `Synced ${ago(Number(job.lastImportDate))} from ${job.name}`;
  return importedAt !== undefined ? `Imported ${ago(importedAt)}` : 'Imported';
}

/** What the job leaves to do: link the source descriptions it could not match to an API. */
export function unlinkedWarning(names: readonly string[] | undefined | null): string | undefined {
  if (!names || names.length === 0) return undefined;
  return names.length === 1
    ? `Source description ${names[0]} is not linked to an API yet: its workflows cannot run.`
    : `Source descriptions ${names.join(', ')} are not linked to an API yet: their workflows cannot run.`;
}
