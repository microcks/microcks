export class ImportJob {
  id: string;
  name: string;
  repositoryUrl: string;
  frequency: string;
  createdDate: Date;
  lastImportDate: Date;
  lastImportError: string;
  active: boolean = false;
  etag: string;

  serviceRefs: ServiceRef[];
}

export class ServiceRef {
  serviceId: string;
  name: string;
  version: string;
}